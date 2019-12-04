package org.dspace.app.cris.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.app.cris.model.CrisConstants;
import org.dspace.app.cris.model.OrganizationUnit;
import org.dspace.app.cris.service.ApplicationService;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.utils.DSpace;


public class OrganizationUnitTreeMaker {

    /** log4j logger */
    private static Logger log = Logger.getLogger(OrganizationUnitTreeMaker.class);

    private static OrganizationUnitTreeMaker instance = null;

    private String ouTreeAsHTML = "";
    private int ouCounter = 0;
    
    private String ouMetrics = " <span class=\"orgunit-shortmetrics\">(<i class=\"fa fa-publications fa-75em\"></i> {{pubCount}} / <i class=\"fa fa-fundings fa-75em\"></i> {{projCount}} / <i class=\"fa fa-researcherprofiles fa-75em\"></i> {{rpCount}})</span>";

    private OrganizationUnitTreeMaker() {
    }

    public static synchronized OrganizationUnitTreeMaker getInstance() {
        if (instance == null) {
            instance = new OrganizationUnitTreeMaker();
            instance.updateOuTree();
        }
        return instance;
    }

    public String getTreeAsHTML() {
        return ouTreeAsHTML;
    }

    public synchronized void updateOuTree() {

        System.out.println("Updating organization unit tree");

        DSpace dspace = new DSpace();
        ApplicationService applicationService = dspace.getServiceManager().getServiceByName("applicationService",
                ApplicationService.class);
        SearchService searchService = dspace.getSingletonService(SearchService.class);

        List<OrganizationUnit> ous = fetchOus(applicationService, searchService);
        ouTreeAsHTML = (ous.size() > 0) ? createOuTreeAsHTML(searchService, ous)
                : "<small style=\"font-style: italic;\">Unfortunately we missed to update the organization unit tree. Please contact us.</small>";

        System.out.println("Finished updating organization unit tree");

    }

    private List<OrganizationUnit> fetchOus(ApplicationService applicationService, SearchService searchService) {

        System.out.println("Fetching organization units from Solr");
        List<OrganizationUnit> ous = new LinkedList<>();

        SolrQuery query = new SolrQuery("{!field f=search.resourcetype}" + CrisConstants.OU_TYPE_ID);
        query.setRows(Integer.MAX_VALUE);

        try {
            QueryResponse response = searchService.search(query);
            SolrDocumentList docList = response.getResults();
            Iterator<SolrDocument> solrDoc = docList.iterator();
            while (solrDoc.hasNext()) {
                SolrDocument doc = solrDoc.next();
                Integer ouId = (Integer) doc.getFirstValue("search.resourceid");
                OrganizationUnit ou = applicationService.get(OrganizationUnit.class, ouId);
                if(ou != null && ou.getStatus()) ous.add(ou);
            }
        } catch (SearchServiceException e) {
            log.error(e);
            return new LinkedList<OrganizationUnit>();
        }

        ous.sort(new Comparator<OrganizationUnit>() {
            @Override
            public int compare(OrganizationUnit o1, OrganizationUnit o2) {
                try {
                    return o1.getMetadata("priority").compareTo(o2.getMetadata("priority"));
                } catch (NullPointerException e) {
                    System.err.println("Unable to sort: \"" + o1.getName() + "\", \"" + o2.getName() + "\". One has no valid \"priority\" field.");
                    return 0;
                }
            }
        });

        return ous;
    }

    private String createOuTreeAsHTML(SearchService searchService, List<OrganizationUnit> ous) {

        System.out.println("Starting to create organisation unit tree as html");
        StringBuilder builder = new StringBuilder();

        builder.append("<div class=\"panel-group\" id=\"accordion\" role=\"tablist\">");

        ouCounter = 0;
        for (OrganizationUnit ou : ous) {

            if (ou != null && ou.getName() != null && ou.getMetadata("parentorgunit") == null) {
                System.out.println("Processing \"" + ou.getName() + "\"...");
                OrganizationUnitWrapper firstLevelOU = createOuHierarchy(searchService, ou, ous);
                builder.append(firstLevelOU.asHTML);
                ouCounter++;
            }
        }

        builder.append("</div>");
        return builder.toString();
    }

    /*
     * Creates single organization unit hierarchy for single organization unit.
     */
    private OrganizationUnitWrapper createOuHierarchy(SearchService searchService, OrganizationUnit ou, List<OrganizationUnit> ous) {

        System.out.println("  Creating hierarchy for \"" + ou.getName() + "\"...");

        StringBuilder builder = new StringBuilder();
        OrganizationUnitWrapper currentOU = new OrganizationUnitWrapper();

        List<OrganizationUnit> children = getChildren(ou, ous);

        SolrQuery pubQuery = new SolrQuery("ubg.faculty.org_authority:" + ou.getCrisID());
        SolrQuery rpQuery = new SolrQuery("crisrp.dept_authority:" + ou.getCrisID());
        SolrQuery projQuery = new SolrQuery("crisproject.deptproject_authority:" + ou.getCrisID());
        pubQuery.setRows(Integer.MAX_VALUE);
        rpQuery.setRows(Integer.MAX_VALUE);
        projQuery.setRows(Integer.MAX_VALUE);
        try {
            currentOU.pubCount = searchService.search(pubQuery).getResults().size();
            currentOU.rpCount = searchService.search(rpQuery).getResults().size();
            currentOU.projCount = searchService.search(projQuery).getResults().size();
        } catch (SearchServiceException e) {
            log.error(e);
        }
        System.out.println("\t\"" + ou.getName() + "\" has " + currentOU.pubCount + " publications, " + currentOU.projCount + " projects, and " + currentOU.rpCount + " researcher");

        if (children.size() > 0) {
            System.out.println("\t\"" + ou.getName() + "\" has " + children.size()
                    + " children");
            builder.append("<div class=\"panel-item\">");
            builder.append("<div class=\"panel-heading collapsed accordion-toggle\" role=\"tab\" id=\"heading");
            builder.append("outreemaker" + ouCounter);
            builder.append("\" data-toggle =\"collapse\" href=\"#collapse");
            builder.append("outreemaker" + ouCounter);
            builder.append("\" aria-expanded=\"false\" aria-controls=\"collapse");
            builder.append("outreemaker" + ouCounter);
            builder.append("\">");
            builder.append("<i style=\"color: rgb(0, 69, 124);\" class=\"fas fa-angle-down\"></i>");
            builder.append("<a href=\"../ou/");
            builder.append(ou.getCrisID());
            builder.append("\" class=\"browseou\">");
            builder.append(ou.getName());
            builder.append("</a>");
            builder.append(ouMetrics);
            builder.append("</div>");
            builder.append("<div id=\"collapse");
            builder.append("outreemaker" + ouCounter);
            builder.append("\" class=\"panel-collapse collapse\" role=\"tabpanel\" aria-labelledby=\"heading");
            builder.append("outreemaker" + ouCounter);
            builder.append("\"> <div class=\"panel-body list-child\">");

            builder.append("<div class=\"panel-item\"><div class=\"panel-heading\" role=\"tab\" id=\"heading");
            builder.append("outreemaker" + ouCounter);
            builder.append("\">");
            builder.append("<a href=\"../ou/");
            builder.append(ou.getCrisID());
            builder.append("\"class=\"org-tree-parent-org org-tree-item-link\"> ");
            builder.append(ou.getName());
            builder.append("</a>");
            builder.append(ouMetrics.replaceAll("\\{\\{pubCount\\}\\}", "" + currentOU.pubCount).replaceAll("\\{\\{projCount\\}\\}", "" + currentOU.projCount).replaceAll("\\{\\{rpCount\\}\\}", "" + currentOU.rpCount));

            builder.append("</div></div>");

            for (OrganizationUnit child : children) {

                ouCounter++;

                OrganizationUnitWrapper childOU = createOuHierarchy(searchService, child, ous);
                currentOU.pubCount += childOU.pubCount;
                currentOU.projCount += childOU.projCount;
                currentOU.rpCount += childOU.rpCount;
                builder.append(childOU.asHTML);
            }

            builder.append("</div></div></div>");

        } else if (children.size() <= 0) {
            System.out.println("\t\"" + ou.getName() + "\" has no children");
            builder.append("<div class=\"panel-item\"><div class=\"panel-heading\" role=\"tab\" id=\"heading");
            builder.append("outreemaker" + ouCounter);
            builder.append("\">");
            builder.append("<a href=\"../ou/");
            builder.append(ou.getCrisID());
            builder.append("\" class=\"fas fa-orgunits org-tree-item-link\"> ");
            builder.append(ou.getName());
            builder.append("</a>");
            builder.append(ouMetrics);
            builder.append("</div></div>");

        } else {
            System.err.println("[ERROR] something went wrong with \"" + ou.getName()
                    + "\": it seems not to be a top level organization unit nor a child (parent: \""
                    + ou.getMetadata("parentorgunit") + "\", child-count: " + children.size() + ")");
        }

        ouCounter++;
        currentOU.asHTML = builder.toString().replaceAll("\\{\\{pubCount\\}\\}", "" + currentOU.pubCount).replaceAll("\\{\\{projCount\\}\\}", "" + currentOU.projCount).replaceAll("\\{\\{rpCount\\}\\}", "" + currentOU.rpCount);
        return currentOU;

    }

    /*
     * Returns all children of corresponding organization unit.
     */
    private List<OrganizationUnit> getChildren(OrganizationUnit parentOu, List<OrganizationUnit> ous) {

        List<OrganizationUnit> children = new LinkedList<>();

        for (OrganizationUnit ou : ous) {
            String parentName = ou.getMetadata("parentorgunit");
            if (parentName != null && parentOu.getName().equals(parentName)) {
                children.add(ou);
            }
        }

        return children;

    }
    
    private class OrganizationUnitWrapper {
        
        int pubCount = 0;
        int rpCount = 0;
        int projCount = 0;
        String asHTML = "";
        
    }

}
