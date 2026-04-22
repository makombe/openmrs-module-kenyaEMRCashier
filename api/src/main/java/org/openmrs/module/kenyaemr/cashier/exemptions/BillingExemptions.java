/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.kenyaemr.cashier.exemptions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.Visit;
import org.openmrs.VisitAttribute;
import org.openmrs.api.context.Context;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An object with details of services and commodities exempted from billing.
 *
 * <p>
 * The class variables should be populated once on startup and should live in
 * memory during
 * application use. Concept identifiers are stored as Strings to accommodate
 * both integer IDs
 * and UUID-style strings found in the global-property config.
 *
 * <p>
 * Supported exemption key conventions:
 * <ul>
 * <li>{@code all} – everyone is exempt (no patient criteria)</li>
 * <li>{@code program:<ProgramName>}– patient must be actively enrolled in the
 * named program</li>
 * <li>{@code age<5} – patient must be younger than 5 years</li>
 * <li>{@code visitAttribute:<val>} – patient's active visit must carry an
 * attribute whose value
 * matches {@code <val>} (case-insensitive)</li>
 * </ul>
 *
 * Sample config JSON stored in global property
 * {@code kenyaemr.billing.exemptions}:
 * 
 * <pre>
 * {
 *   "services": {
 *     "all":              [{"concept":"167441","description":"PCR"}],
 *     "program:HIV":      [{"concept":"1000051","description":"Registration"},
 *                          {"concept":"856","description":"HIV Viral Load"}],
 *     "program:TB":       [{"concept":"162202","description":"GeneXpert"}],
 *     "age<5":            [{"concept":"32","description":"Malaria Smear"}],
 *     "visitAttribute:prisoner": [{"concept":"32","description":"Malaria Smear"}]
 *   },
 *   "commodities": {}
 * }
 * </pre>
 */
public abstract class BillingExemptions {

    private static final Log LOG = LogFactory.getLog(BillingExemptions.class);

    /**
     * Map of exemption-key → set of exempt concept identifiers (IDs or UUIDs as
     * Strings).
     * Keys follow the conventions documented in the class Javadoc.
     */
    public static Map<String, Set<String>> SERVICES;

    /**
     * Map of exemption-key → set of exempt commodity concept identifiers.
     */
    public static Map<String, Set<String>> COMMODITIES;

    /**
     * Implementations must populate {@link #SERVICES} and {@link #COMMODITIES}
     * from the site's global-property configuration.
     */
    public abstract void buildBillingExemptionList();

    public static void setSERVICES(Map<String, Set<String>> services) {
        BillingExemptions.SERVICES = services;
    }

    public static void setCOMMODITIES(Map<String, Set<String>> commodities) {
        BillingExemptions.COMMODITIES = commodities;
    }

    /**
     * Returns {@code true} when the given {@code conceptIdentifier} (an integer ID
     * or UUID string)
     * is exempt for {@code patient} according to the currently loaded
     * {@link #SERVICES} map.
     *
     * @param patient           the patient being billed
     * @param conceptIdentifier the concept ID or UUID of the service/commodity
     *                          being billed
     * @return {@code true} if the patient-service combination is exempt from
     *         billing
     */
    public static boolean isServiceExempted(Patient patient, String conceptIdentifier) {
        if (SERVICES == null || SERVICES.isEmpty() || patient == null || conceptIdentifier == null) {
            return false;
        }
        return isExemptedInMap(SERVICES, patient, conceptIdentifier);
    }

    /**
     * Returns {@code true} when the given {@code conceptIdentifier} is exempt for
     * {@code patient}
     * according to the currently loaded {@link #COMMODITIES} map.
     */
    public static boolean isCommodityExempted(Patient patient, String conceptIdentifier) {
        if (COMMODITIES == null || COMMODITIES.isEmpty() || patient == null || conceptIdentifier == null) {
            return false;
        }
        return isExemptedInMap(COMMODITIES, patient, conceptIdentifier);
    }

    private static boolean isExemptedInMap(Map<String, Set<String>> exemptionMap,
            Patient patient,
            String conceptIdentifier) {
        for (Map.Entry<String, Set<String>> entry : exemptionMap.entrySet()) {
            String key = entry.getKey();
            Set<String> exemptedConcepts = entry.getValue();

            if (exemptedConcepts == null || !containsConcept(exemptedConcepts, conceptIdentifier)) {
                continue; // this concept is not in this exemption group – skip quickly
            }

            // Concept is listed – now check whether the patient meets the key's criteria
            if (patientMeetsCriteria(key, patient)) {
                LOG.debug(String.format(
                        "Patient %d is exempt from concept %s via key '%s'",
                        patient.getPatientId(), conceptIdentifier, key));
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code conceptIdentifier} is present in
     * {@code conceptSet}.
     * Comparison is case-insensitive and trims whitespace.
     */
    private static boolean containsConcept(Set<String> conceptSet, String conceptIdentifier) {
        String normalized = conceptIdentifier.trim();
        for (String stored : conceptSet) {
            if (stored.trim().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates whether {@code patient} satisfies the criteria encoded in
     * {@code key}.
     *
     * <ul>
     * <li>{@code "all"} → always true</li>
     * <li>{@code "program:<name>"} → patient is actively enrolled in the named
     * program</li>
     * <li>{@code "age<5"} → patient age is 0–4</li>
     * <li>{@code "visitAttribute:<value>"} → patient's active visit has a matching
     * attribute value</li>
     * </ul>
     */
    private static boolean patientMeetsCriteria(String key, Patient patient) {
        if (key == null) {
            return false;
        }

        String normalizedKey = key.trim().toLowerCase();

        if ("all".equals(normalizedKey)) {
            return true;
        }

        if (normalizedKey.startsWith("program:")) {
            String programName = key.substring("program:".length()).trim();
            return isEnrolledInProgram(patient, programName);
        }

        if (normalizedKey.startsWith("age<")) {
            try {
                int maxAge = Integer.parseInt(normalizedKey.substring("age<".length()).trim());
                Integer patientAge = patient.getAge();
                return patientAge != null && patientAge < maxAge;
            } catch (NumberFormatException e) {
                LOG.warn("Invalid age exemption key (cannot parse age): " + key);
                return false;
            }
        }

        if (normalizedKey.startsWith("visitattribute:")) {
            String attributeValue = key.substring("visitAttribute:".length()).trim();
            return hasActiveVisitAttribute(patient, attributeValue);
        }

        LOG.warn("Unknown exemption key pattern encountered, skipping: " + key);
        return false;
    }

    /**
     * Returns {@code true} when the patient has an active (not completed) enrolment
     * in any program whose name matches {@code programName} (case-insensitive).
     */
    private static boolean isEnrolledInProgram(Patient patient, String programName) {
        try {
            List<PatientProgram> patientPrograms = Context.getProgramWorkflowService().getPatientPrograms(patient, null,
                    null, null, null, null, false);

            for (PatientProgram pp : patientPrograms) {
                boolean nameMatches = pp.getProgram() != null
                        && programName.equalsIgnoreCase(pp.getProgram().getName());
                boolean isActive = pp.getDateCompleted() == null; // not yet completed = active
                if (nameMatches && isActive) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error checking program enrollment for patient " + patient.getPatientId(), e);
        }
        return false;
    }

    /**
     * Returns {@code true} when the patient has an ongoing visit that contains at
     * least one
     * {@link VisitAttribute} whose value equals {@code attributeValue}
     * (case-insensitive).
     *
     * <p>
     * Only visits that have not yet been stopped (i.e.,
     * {@code stopDatetime == null}) are
     * considered active.
     */
    private static boolean hasActiveVisitAttribute(Patient patient, String attributeValue) {
        try {
            List<Visit> visits = Context.getVisitService().getActiveVisitsByPatient(patient);
            for (Visit visit : visits) {
                Collection<VisitAttribute> attributes = visit.getAttributes();
                if (attributes == null) {
                    continue;
                }
                for (VisitAttribute attribute : attributes) {
                    if (!attribute.getVoided()
                            && attribute.getValueReference() != null
                            && attribute.getValueReference().trim().equalsIgnoreCase(attributeValue)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error checking visit attributes for patient " + patient.getPatientId(), e);
        }
        return false;
    }
}