/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.lookup;

import java.util.LinkedList;
import java.util.List;

import gr.ekt.bte.core.MutableRecord;
import gr.ekt.bte.core.Record;
import gr.ekt.bte.core.StringValue;
import gr.ekt.bte.core.Value;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.XMLUtils;
import org.dspace.content.DCDate;
import org.dspace.submit.util.SubmissionLookupPublication;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Mapping from DataCite XML to BTE Batch Import Service Format
 * 
 * Specification we used: https://doi.org/10.14454/3w3z-sa82
 * (Link to chapters in the specification are marked with # )
 *
 * @author Andrea Bollini
 * @author Kostas Stamatis
 * @author Luigi Andrea Pascarelli
 * @author Panagiotis Koutsourakis
 * @author Philipp Rumpf (University of Bamberg)
 * @author Florian Gantner (University of Bamberg)
 */
public class DataCiteUtils
{
	/*For debugging*/
    private static Logger log = Logger.getLogger(DataCiteUtils.class);

	public static Record convertDOIDomToRecord(Element dataRoot, String doi, String source)
	{
		//Check the parent element
		if (dataRoot == null) {
			throw new RuntimeException("Unknown XML Response");
		}
		if(!dataRoot.getTagName().contentEquals("resource")){
			throw new RuntimeException("Unknown XML Response, missing resource tag");
		}

		
		MutableRecord record = new SubmissionLookupPublication("datacite");
		//Loop through the Elements
		{
			// #1 Identifier
			try {
				Element el  = XMLUtils.getSingleElement(dataRoot, "identifier");
					if(el.getAttribute("identifierType") != null && el.getAttribute("identifierType").contentEquals("DOI")) {
						record.addValue("doi", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						
					}
			}catch(Exception e) {
				log.error("#1 " + e.getMessage());
			}
		}
		{
			// #2 Creator
			try {
			List<Element> els = XMLUtils.getElementList(dataRoot, "creators");
			
			for(Element el : els) {
				for(Element creator: XMLUtils.getElementList(el, "creator")) {
					NodeList nd = creator.getChildNodes();
					boolean set = false;
					for(int i = 0; i < nd.getLength(); i++) {
						Node n = nd.item(i);
						/*Distinguish Personal and Organization Creators*/
						if(n.getNodeName().contentEquals("creatorName")) {
							if(n.hasAttributes()) {
								try {
									boolean personal = n.getAttributes().getNamedItem("nameType").getTextContent().trim().equalsIgnoreCase("Personal");
										if(personal) {
											record.addValue("author", new StringValue(n.getTextContent() != null ? n.getTextContent().trim() : null));
											set = true;
											}
								}catch(Exception e) {
									log.error(e.getMessage());
								}
								if(!set) {
								try {
									boolean org = n.getAttributes().getNamedItem("nameType").getTextContent().trim().equalsIgnoreCase("Organizational");
											if(org) {
												record.addValue("corporationauthor", new StringValue(n.getTextContent() != null ? n.getTextContent().trim() : null));
												set = true;
											}
									}catch(Exception e) {
										log.error(e.getMessage());
									}
								}
							}
							if(!set) {
								//By default if it's nameType not specified, add some own field
								record.addValue("authorunspecified", new StringValue(n.getTextContent() != null ? n.getTextContent().trim() : null));
								set = true;
							}
							
							//further affiliation and nameIdentifier informations about persons are ignored
							//Affiliation: Would need some placeholder for the authority value, if the belonging editoraffiliation points to an OU, not supported by bte
							
						}
					}
					}
				}
			} catch (Exception e) {
				log.error("#2 " + e.getMessage());
			}
		}
		{
			// #3 Title
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "titles");
					for(Element el : els) {
						if(el.hasAttribute("titleType")) {
							switch(el.getAttribute("titleType")) {
							case "AlternativeTitle":
								record.addValue("titleAlternative", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								break;
								//TODO: consider other types
							case "Subtitle":
								record.addValue("titleSubtitle", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								break;
							case "TranslatedTitle":
								record.addValue("titleTranslated", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								break;
							case "Other":
								record.addValue("titleOther", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								break;
							}
						}else {
							record.addValue("title", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						}
					}
			}catch(Exception e) {
				log.error("#3 " + e.getMessage());
			}
		}
		{
			// #4 Publisher
			try {
				Element el  = XMLUtils.getSingleElement(dataRoot, "publisher");
				record.addValue("publisher", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
			}catch(Exception e) {
				log.error("#4 " + e.getMessage());
			}
		}
		{
			// #5 PublicationYear
			try {
				Element el  = XMLUtils.getSingleElement(dataRoot, "publicationYear");
						record.addValue("issued", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
			}catch(Exception e) {
				log.error("#5 " + e.getMessage());
			}
		}
		{
			// #6 Subject
			// Main issue here are Free-Text values. Those hurden the identification of some specific scheme (dewey, loc etc...).
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "subjects");
					for(Element el : els) {
						
						//Prefer existing uri's than texts
						if(el.hasAttribute("valueURI")) {
							String val = el.getAttribute("valueURI");
							record.addValue("subjectURI", new StringValue(val));
						}else {
							record.addValue("subject", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						}
						
						//To Identifiy some scheme, e.g. ddc/dewey subjects or LOC - we can scheck the subjectScheme(Free Text) or the schemeURI (if known) 
						/*
						 * boolean added = false;
						if(el.hasAttribute("subjectScheme")) {
							switch(el.getAttribute("subjectScheme")) {
							case "dewey":
								record.addValue("subjectddc", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								added = true;
								break;
							case "Library of Congress Subject Headings (LCSH)":
								record.addValue("subjectloc", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								added = true;
								break;
							default:
								//
							}
							
						} 
						if(!added && el.hasAttribute("schemeURI")) {
							switch(el.getAttribute("schemeURI")) {
							//Cool URI's don't change
							case "https://id.loc.gov/authorities/subjects.html":
								record.addValue("subjectloc", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
								added = true;
								break;
							default:
								//
						}
						if(!added){
							record.addValue("subjects", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));	
						}
						*/
					}
			}catch(Exception e) {
				log.error("#6 " + e.getMessage());
			}
		}
		{
			// #7 Contributor
			//Contributors can be distinguished by their contributorType
			 
			try {
			List<Element> els = XMLUtils.getElementList(dataRoot, "contributors");
			
			for(Element el : els) {
				for(Element contributor: XMLUtils.getElementList(el, "contributor")) {
					NodeList nd = contributor.getChildNodes();
					for(int i = 0; i < nd.getLength(); i++) {
						Node n = nd.item(i);
						if(n.getNodeName().contentEquals("contributorName")) {
							if(n.hasAttributes()) {
								try {
									String val = n.getTextContent() != null ? n.getTextContent().trim() : null;
									switch(n.getAttributes().getNamedItem("contributorType").getTextContent().trim()){
									case "HostingInstitution":
									record.addValue("contributorcorporation", new StringValue(val));
									break;
									case "Sponsor":
									record.addValue("contributorsponsor", new StringValue(val));
									break;
									case "Editor":
									record.addValue("contributoreditor", new StringValue(val));
									break;
									case "ContactPerson":
									case "DataCollector":
									case "DataCurator":
									case "DataManager":
									case "Distributor":
									case "Producer":
									case "ProjectLeader":
									case "ProjectManager":
									case "ProjectMember":
									case "RegistrationAgency":
									case "RegistrationAuthority":
									case "RelatedPerson":
									case "Researcher":
									case "ResearchGroup":
									case "Supervisor":
									case "WorkPackageLeader":
									case "Other":
									default:
									record.addValue("contributor", new StringValue(val));
									}
								}catch(Exception e) {
									log.error(e.getMessage());
								}

							}
							//further nameIdentifier and affiliation are ignored
						}
					}
				}
			}
			} catch (Exception e) {
				log.error("#7 " + e.getMessage());
			}
		}
		{
			// #8 Date
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "dates");
					for(Element el : els) {
						switch(el.getAttribute("dateType")) {
						case "Accepted":
							record.addValue("dateaaccepted",  new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
							break;
						case "Available":
							record.addValue("dateavailable",  new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
							break;
						case "Issued":
							record.addValue("dateissued",  new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
							break;
						case "Copyrighted":
						case "Collected":
						case "Created":
						case "Submitted":
						case "Updated":
						case "Valid":
						case "Withdrawn":
						case "Other":
						default:
							//dateType information is not saved, as well as futher dateInformation
							record.addValue("date",  new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						}
						
					}
			}catch(Exception e) {
				log.error("#8 " + e.getMessage());
			}
		}
		{
			// #9 Language
			/* Recommended values are taken from IETF BCP 47, ISO 639-1 language codes.*/
			try {
				Element el  = XMLUtils.getSingleElement(dataRoot, "language");
				record.addValue("language", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
			}catch(Exception e) {
				log.error("#9 " + e.getMessage());
			}
		}
		{
			//#10 ResourceType
			
			try {
				List<Element> resourceType = XMLUtils.getElementList(dataRoot, "resourceType");
				for(Element el : resourceType) {
					if(el.hasAttribute("resourceTypeGeneral")) {
						record.addValue("generaltype", new StringValue(el.getAttribute("resourceTypeGeneral")));
					}
					/*Some specific ResourceType (Freetext) could be mapped here*/
						//record.addValue("subsubtype", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null)); // = Other ?
				}
			} catch (Exception e) {
				log.error("#10 " + e.getMessage());
			}
		}
		{
			//#11 AlternativeIdentifiers -> Freetext
			//this could for example be the handle of some other repository. We do not expect standarized values here
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "alternateIdentifiers");
				for(Element el : els) {
					switch(el.getAttribute("alternateIdentifierType")) {
					case "url":
						record.addValue("identifieralternativeurl", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						//TODO: other identifiertypes
						break;
					default:
						//
						record.addValue("identifieralternative", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
					}
				}
			} catch (Exception e) {
				log.error("#11" + e.getMessage());
			}
		}
		{
			//#12 RelatedIdentifier
			/*
			//Problems are Free-Text Values and possible relationTypes, which offers more combinations.
			 * This can not be expressed standard dspace-cris input-forms and datamodel, but in nested metadata that must be defined by your own.
			* Relation: RelatedIdentifier |  relatedIdentifierType | relationType
			 * */
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "relatedIdentifiers");
				for(Element el : els) {
					//TODO: consider relatedIdentifierType and relationType
					switch(el.getAttribute("relatedIdentifierType")) {
					case "DOI":
					case "doi":
						record.addValue("relateddoi", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "ISSN":
					case "issn":
						record.addValue("relatedissn", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "uri":
					case "URI":
						record.addValue("relateduri", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "handle":
					case "HANDLE":
						//
						break;
					case "PMID":
						record.addValue("relatedpubmedID", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "url":
					case "URL":
						record.addValue("relatedurl", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "ISBN":
					case "isbn":
						record.addValue("relatedisbn", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "ARK":
					case "arXiv":
					case "bibcode":
					case "EAN13":
					case "EISSN":
					case "IGSN":
					case "ISTC":
					case "LISSN":
					case "LSID":
					case "PURL":
					case "UPC":
					case "w3id":
					default:
						record.addValue("relatedidentifier", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					}
					
					/*
					switch(el.getAttribute("relationType")) {
					case "IsCitedBy":
					case "Cites":
					case "IsSupplementTo":
					case "IsSupplementedBy":
					case "IsContinuedBy":
					case "Continues":
					case "IsDescribedBy":
					case "Describes":
					case "HasMetadata":
					case "IsMetadataFor":
					case "HasVersion":
					case "IsVersionOf":
					case "IsNewVersionOf":
					case "IsPreviousVersionOf":
					case "IsPartOf":
					case "HasPart":
					case "IsPublishedIn":
					case "IsReferencedBy":
					case "References":
					case "IsDocumentedBy":
					case "Documents":
					case "IsCompiledBy":
					case "Compiles":
					case "IsVariantFormOf":
					case "IsOriginalFormOf":
					case "IsIdenticalTo":
					case "IsReviewedBy":
					case "Reviews":
					case "IsDerivedFrom":
					case "IsSourceOf":
					case "IsRequiredBy":
					case "Requires":
					case "IsObsoletedByObsoletes":
					default:
					} 
					*/
				}
			} catch (Exception e) {
				log.error("#12 " + e.getMessage());
			}
		}
		{
			//#13 Size : Free Text
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "size");
				for(Element el : els) {
						record.addValue("size", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
				}
			} catch (Exception e) {
				log.error("#13" + e.getMessage());
			}
		}
		{
			//#14 Format : Free Text, if possible some mine/type
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "format");
				for(Element el : els) {
					record.addValue("format", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
				}
			} catch (Exception e) {
				log.error("#14" + e.getMessage());
			}
		}
		{
			//#15 Version : Free Text
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "version");
				for(Element el : els) {
					record.addValue("version", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
				}
			} catch (Exception e) {
				log.error("#15" + e.getMessage());
			}
		}
		{
			//#16 Rights: Can be free text and optionally some url defined in some scheme
			//
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "rightsList");
				for(Element el : els) {
					//Prefer url to free text token
					if(el.hasAttribute("rightsURI")) {
						String rightsURI = el.getAttribute("rightsURI");
						record.addValue("rightsURI", new StringValue(!el.getAttribute("rightsURI").trim().isEmpty() ? el.getAttribute("rightsURI").trim() : null));
					}else {
						record.addValue("url", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
					}
					//Optionally: use some mapping derived from the scheme, if you use some controlled value list for licence-informations 
				}
			} catch (Exception e) {
				log.error("#16" + e.getMessage());
			}
		}
		{
			//#17 Description
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "descriptions");
				for(Element el : els) {
					switch(el.getAttribute("descriptionType")) {
					case "Abstract":
					case "abstract":
						record.addValue("abstract", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
						break;
					case "Methods":
					case "SeriesInformation":
					case "TableOfContents":
					case "TechnicalInfo":
					case "Other":
						default:
						record.addValue("abstractother", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
					}
					
				}
			} catch (Exception e) {
				log.error("#17 " + e.getMessage());
			}
		}
		{
			//#18 Geolocation
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "geolocation");
				for(Element el : els) {
					// TOOD: More attributes to consider - save as plain text
					record.addValue("geoloc", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
				}
			} catch (Exception e) {
				log.error("#18 " + e.getMessage());
			}
		}
		{
			//#19 FundingReference
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "fundingreferences");
				for(Element el : els) {
					// TOOD: More attributes to consider - save as plain text
					record.addValue("funding", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
				}
			} catch (Exception e) {
				log.error("#19" + e.getMessage());
			}
			
			//#20 Related Item
			try {
				List<Element> els = XMLUtils.getElementList(dataRoot, "relateditems");
				for(Element el : els) {
					// TOOD: More attributes to consider - save as plain text
					record.addValue("relateditem", new StringValue(el.getTextContent() != null ? el.getTextContent().trim() : null));
				}
			} catch (Exception e) {
				log.error("#20" + e.getMessage());
			}
		}
		
		/* General Provenance Informations*/
		LinkedList<Value> comments = new LinkedList<Value>();
		comments.add(new StringValue("DOI-Import " + doi + " Source " + source + " " + DCDate.getCurrent()));
		record.addField("internalcomment", comments);

		return record;
	}
}
