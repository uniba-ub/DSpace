/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.cris.rdf.storage;

import java.sql.SQLException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.cris.model.ACrisObject;
import org.dspace.app.cris.rdf.RDFConfiguration;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Site;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.dspace.app.cris.rdf.storage.LocalCrisidURIGenerator;
import org.dspace.app.cris.rdf.storage.URIGenerator;
import org.dspace.core.ConfigurationManager;

/**
 *
 * @author Pascal-Nicolas Becker (dspace -at- pascal -hyphen- becker -dot- de)
 *         
 *         Extension for LocalURIGenerator. Using Crisid as Identifier for
 *         Cris-Objects
 * @commentor Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class LocalCrisidURIGenerator implements URIGenerator {
	private static final Logger log = Logger.getLogger(LocalCrisidURIGenerator.class);

	@Override
	public String generateIdentifier(Context context, int type, int id, String handle, String[] identifiers)
			throws SQLException {
		String urlPrefix = RDFConfiguration.getDSpaceRDFModuleURI() + "/" ;

		if (type == Constants.SITE) {
			return urlPrefix + Site.getSiteHandle();
		}

		if (type == Constants.COMMUNITY || type == Constants.COLLECTION || type == Constants.ITEM) {
			if (StringUtils.isEmpty(handle)) {
				throw new IllegalArgumentException("Handle is null");
			}
			return urlPrefix + handle;
		}

		return null;
	}

	@Override
	public String generateIdentifier(Context context, DSpaceObject dso) throws SQLException {
		StringBuilder sb = new StringBuilder();
		if (dso instanceof ACrisObject) {
			ACrisObject aco = (ACrisObject) dso;
			// Adapt here for URI-Generation
			// Path Pattern Replace
			sb.append(RDFConfiguration.getDSpaceRDFModuleURI());
			sb.append("/");
			sb.append(addpath(dso, aco.getCrisID()));
			return sb.toString();
		}

		if (dso.getType() != Constants.SITE && dso.getType() != Constants.COMMUNITY
				&& dso.getType() != Constants.COLLECTION && dso.getType() != Constants.ITEM) {
			return null;
		}

		// for dso: use path and handle as parameters
		return generateIdentifier(context, dso.getType(), dso.getID(), addpath(dso, dso.getHandle()),
				dso.getIdentifiers(context));
	}

	/**
	 *  Helping Method for generating path between dspace /rdf/ path and suffix 
	 *  */
	private String addpath(DSpaceObject dso, String handle) {
		StringBuilder sb = new StringBuilder();
		// see rdf.cfg for details of configuration Setting
		ConfigurationService configurationService =
                new DSpace().getConfigurationService();
		String path1 = configurationService.getProperty("rdf.crisgenerator.general.prefix");
		
		if(!(dso instanceof ACrisObject)) {
			boolean prefix = (Boolean) configurationService.getPropertyAsType("rdf.crisgenerator.handle.prefix", Boolean.class);
			if (path1 != null && !path1.isEmpty()) {
				sb.append(path1);
			}
			if(!prefix) {
				String handleprefix = configurationService.getProperty("handle.prefix");
				handle = handle.replace(handleprefix + "/", "");
			}
				sb.append(dso.getHandle());
		}else {
			String path2 = configurationService.getProperty("rdf.crisgenerator.cris.prefix");
			boolean path3 = (Boolean) configurationService.getPropertyAsType("rdf.crisgenerator.cris.entityprefix", Boolean.class);
			ACrisObject aco = (ACrisObject) dso;
			if (path1 != null && !path1.isEmpty())
				sb.append(path1);
			if (path2 != null && !path2.isEmpty())
				sb.append(path2);
			if (path3 == true) {
				sb.append(aco.getAuthorityPrefix());
				sb.append("/");
			}
			sb.append(handle);
		}
		return sb.toString();
	}
}
