/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.submit.lookup;

import gr.ekt.bte.core.Record;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpException;

import org.dspace.core.Context;

/**
 * @author Andrea Bollini
 * @author Kostas Stamatis
 * @author Luigi Andrea Pascarelli
 * @author Panagiotis Koutsourakis
 * @author Philipp Rumpf (University of Bamberg)
 * @author Florian Gantner (University of Bamberg)
 */
public class DataCiteOnlineDataLoader extends NetworkSubmissionLookupDataLoader
{
    private DataCiteService DataCiteService = new DataCiteService();

    private boolean searchProvider = true;
    private String doiURL;
    private String queryFieldName;

    public void setDataCiteService(DataCiteService DataCiteService)
    {
	this.DataCiteService = DataCiteService;
    }

    @Override
    public List<String> getSupportedIdentifiers()
    {
	return Arrays.asList(new String[] { DOI });
    }

    public void setSearchProvider(boolean searchProvider)
    {
	this.searchProvider = searchProvider;
    }

    public void setdoiURL(String url)
    {
	doiURL = url;
    }

    public void setQueryFieldName(String name)
    {
	queryFieldName = name;
    }

    @Override
    public boolean isSearchProvider()
    {
	return searchProvider;
    }

    @Override
    public List<Record> getByIdentifier(Context context,
	    Map<String, Set<String>> keys) throws HttpException, IOException
    {
	List<Record> results = new ArrayList<Record>();
	if (keys != null)
	{
	    Set<String> dois = keys.get(DOI);
	    List<Record> items = new ArrayList<Record>();
	    if (dois != null && dois.size() > 0)
	    {
		for (String doi : dois)
		{
			Record record = DataCiteService.getByDOI(doi, doiURL, queryFieldName);

			if (record != null)
				items.add(record);
		}
	    }

	    for (Record item : items)
	    {
		results.add(convertFields(item));
	    }
	}
	return results;
    }

    @Override
    public List<Record> search(Context context, String title, String author,
	    int year) throws HttpException, IOException
    {
	List<Record> results = new ArrayList<Record>();
	List<Record> items = DataCiteService.searchByTerm(title, author, year, doiURL, queryFieldName);
	for (Record item : items)
	{
	    results.add(convertFields(item));
	}
	return results;
    }
}
