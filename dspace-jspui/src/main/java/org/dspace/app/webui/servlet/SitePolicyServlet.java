/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.webui.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

/**
 * Servlet that sets site_policy_accepted when a user accepts the site policy
 * so it doesn't show up again.
 * 
 * @author Cornelius Matějka <cornelius.matejka@uni-bamberg.de>
 */
public class SitePolicyServlet extends DSpaceServlet
{
    /** log4j logger */
    private static Logger log = Logger.getLogger(SitePolicyServlet.class);

    protected void doDSPost(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        EPerson currentUser = context.getCurrentUser();
        boolean policyReadChecked = Boolean.parseBoolean(request.getParameter("policyReadChecked"));
        currentUser.setSitePolicyAccepted(policyReadChecked);
        currentUser.update();
        context.complete();
    }
}
