/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.lookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;

import gr.ekt.bte.core.Record;

/**
 * Import from Datacite API
 * based on the ISBNService and the methods/settings from the other onlinedataloaders
 * https://support.datacite.org/docs/api
 * 
 * @author Andrea Bollini
 * @author Kostas Stamatis
 * @author Luigi Andrea Pascarelli
 * @author Panagiotis Koutsourakis
 * @author Philipp Rumpf (University of Bamberg)
 * @author Florian Gantner (University of Bamberg)
 */
public class DataCiteService
{
    private static final Logger log = Logger.getLogger(DataCiteService.class);
	
	private int timeout = 1000;

	/**
	 * How long to wait for a connection to be established.
	 *
	 * @param timeout milliseconds
	 */
	public void setTimeout(int timeout)
	{
		this.timeout = timeout;
	}

	public List<Record> searchByTerm(String title, String author, int year, String baseurl, String field)
			throws HttpException, IOException
	{
		//Not Tested
		//https://support.datacite.org/docs/api
		StringBuffer query = new StringBuffer();
		if (StringUtils.isNotBlank(title))
		{
			query.append("titles.title:\"").append(title).append("\"");
		}
		if (StringUtils.isNotBlank(author))
		{
			if (query.length() > 0)	query.append(" AND ");
			query.append("creators.name:\"").append(author).append("\"");
		}
		if(year != -1){
			if (query.length() > 0) query.append(" AND ");
			query.append("publicationYear:"+year);
			}
		return search(query.toString(), "", 10, baseurl, field);
	}

	private List<Record> search_datacitexml(String query, String doi, int max_result, String baseurl, String field)
			throws HttpException, IOException
	{
		List<Record> results = new ArrayList<Record>();
		CloseableHttpClient client = null;
		HttpGet method = null;
		InputStream is = null;
		Set<String> furtherlookup = new HashSet<String>(); 
		try
		{
			client = new DefaultHttpClient();
			HttpParams params = client.getParams();
			params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
			
			//Build URI to doi or to search 
			if((doi == null || doi.isEmpty()) && query != null && !query.isEmpty()) {
				//Search query
				doi = "";
			}else {
				//direct access to doi
				baseurl += "/";
			}
			
			URIBuilder uriBuilder = new URIBuilder(baseurl + doi);
			if((doi == null || doi.isEmpty()) && query != null && !query.isEmpty()) {
				//Search query
				uriBuilder.addParameter(field, query);
			}
			method = new HttpGet(uriBuilder.build());
			 
			HttpResponse response = client.execute(method);
			StatusLine responseStatus = response.getStatusLine();
			int statusCode = responseStatus.getStatusCode();
			
			if (statusCode == HttpStatus.SC_BAD_REQUEST) {
				throw new RuntimeException("DOI query is not valid");
			} else if(statusCode != HttpStatus.SC_OK) {
				throw new RuntimeException("Http call failed: "
						+ responseStatus);
			}
				// We expect some base64-decoded xml wrapped inside the json response.*/
				is = response.getEntity().getContent();
				StringWriter writer = new StringWriter();
	            IOUtils.copy(is, writer, "UTF-8");
	            String source = writer.toString();

	            JSONObject obj = new JSONObject(source);
	         	// Get decoded xml in json response: /data/attributes/xml/
	            if(obj.has("data")) {
	            	
	            	Object data = obj.get("data");
	            	// distinguish single result (/<doi>) in array or (by search) multiple results (?query=doi:<doi>	
	            	if(data != null && !data.equals(JSONObject.NULL)) {
	            		JSONArray datas = new JSONArray();
	            		if(data instanceof JSONObject) {
		            		datas.put(data);
	            		}else if(data instanceof JSONArray){
	            			datas = (JSONArray) data;
	            		}else {
	    					throw new RuntimeException("Unknown Response with unexpected JSON content");
	            		}
	            		
	                    for (int i = 0; i < datas.length(); i++) {
	                        JSONObject object = datas.getJSONObject(i);
	                        JSONObject attributes = object.getJSONObject("attributes");
		            		
		            		/*Decode XML for further Processing*/
	                        if(attributes.has("xml")) {
	                        String xmlencoded = (String) attributes.get("xml");
		            		if(xmlencoded == null || xmlencoded.isEmpty()) continue;

			            		byte[] val = Base64.getDecoder().decode(xmlencoded);
			    				DocumentBuilderFactory db = DocumentBuilderFactory
			    						.newInstance();
			    				db.setValidating(false);
			    				db.setIgnoringComments(true);
			    				db.setIgnoringElementContentWhitespace(true);
			    				DocumentBuilder xmlparser = db.newDocumentBuilder();
			    				String xmlplain = new String(val,StandardCharsets.UTF_8);
	
			    				Document inDoc = xmlparser.parse(IOUtils.toInputStream(xmlplain, StandardCharsets.UTF_8));
			    				Record recorditem = DataCiteUtils
		    							.convertDOIDomToRecord(inDoc.getDocumentElement(), doi, baseurl);
		    					if (recorditem != null) results.add(recorditem);
	    					
	                        }
	                        /*else if(!query.isEmpty()) {
	                        	//No XML? Possible some search query. Add doi's to list and call the API again without query 
	                        	if(attributes.has("doi") && !attributes.get("doi").equals(JSONObject.NULL)) {
	                        		furtherlookup.add((String)attributes.get("doi"));
	                        	}
	                        } */ 
	                    }
	            	}
	            }else {
					throw new RuntimeException("Unknown Response with unexpected JSON content - no data attribute");
	            }
		} catch (URISyntaxException ex)
		{
			log.error(ex.getMessage(), ex);
		}
    	catch (RuntimeException rt)
    	{
    		log.error(rt.getMessage(), rt);
    	}
		catch (Exception e)
		{
    		log.error(e.getMessage(), e);
		}
		finally
		{
			if (method != null)
			{			    
				method.releaseConnection();
			}
			if (client != null) 
			{
			    client.close();
			}
			if(is != null) {
				is.close();
			}
		}
		//Got some further doi's to lookup from the query? fetch them single-based (not very performant)
		if(!furtherlookup.isEmpty()) {
			for(String singledoi : furtherlookup) {
				results.addAll(search_datacitexml("", singledoi, 10, baseurl, field));
			}
		}
		return results;
	}

	private List<Record> search(String query, String doi, int max_result, String url, String name)
			throws IOException, HttpException
	{
		return search_datacitexml(query, doi, max_result, url, name);
	}

	public Record getByDOI(String raw, String url, String name) throws HttpException, IOException
	{
		if (StringUtils.isNotBlank(raw))
		{
			//normalize identifier instead
			raw = SubmissionLookupUtils.normalizeDOI(raw);
			raw = raw.replace("^https?://(dx\\.)?doi.org/", "");
			List<Record> result = search(null, raw, 1, url, name);
			if (result != null && result.size() > 0)
			{
				return result.get(0);
			}
		}
		return null;
	}
}
