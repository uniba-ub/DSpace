/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.orcid.client;

import org.dspace.app.orcid.model.OrcidTokenResponseDTO;
import org.orcid.jaxb.model.v3.release.record.Person;
import org.orcid.jaxb.model.v3.release.record.Record;

/**
 * Interface for classes that allow to contact ORCID.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public interface OrcidClient {

    /**
     * Exchange the authorization code for an ORCID iD and 3-legged access token.
     * The authorization code expires upon use.
     *
     * @param  code the authorization code
     * @return      the ORCID token
     */
    OrcidTokenResponseDTO getAccessToken(String code);

    /**
     * Retrieves a summary of the ORCID person related to the given orcid.
     *
     * @param  accessToken the access token
     * @param  orcid       the orcid id of the record to retrieve
     * @return             the Person
     */
    Person getPerson(String accessToken, String orcid);

    /**
     * Retrieves a summary of the ORCID record related to the given orcid.
     *
     * @param  accessToken the access token
     * @param  orcid       the orcid id of the record to retrieve
     * @return             the Record
     */
    Record getRecord(String accessToken, String orcid);
}