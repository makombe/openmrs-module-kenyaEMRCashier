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
import org.openmrs.Location;
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
 * The class variables should be populated once on startup and live in memory
 * during application use. Concept identifiers are stored as Strings to
 * accommodate both integer IDs and UUID-style strings found in the
 * global-property config.
 *
 * <h2>Supported exemption key conventions</h2>
 * <ul>
 * <li>{@code all}
 * – every patient is exempt (no patient criteria).</li>
 * <li>{@code program:<ProgramName>}
 * – patient must be actively enrolled in the named program;
 * only the concepts listed are exempt.</li>
 * <li>{@code program:<ProgramName>:all}
 * – patient must be actively enrolled in the named program;
 * <em>all</em> services/commodities are exempt (concept list is
 * ignored). The value array should be left empty {@code []}.</li>
 * <li>{@code location:<LocationName>}
 * – the provider's current session location (or, as a fallback, the
 * patient's active visit location) must match the named location;
 * only the concepts listed are exempt.</li>
 * <li>{@code location:<LocationName>:all}
 * – same location check as above; <em>all</em> services/commodities
 * are exempt. The value array should be left empty {@code []}.</li>
 * <li>{@code age<N}
 * – patient must be younger than N years.</li>
 * <li>{@code visitAttribute:<value>}
 * – the patient's active visit must carry an attribute whose value
 * matches {@code <value>} (case-insensitive).</li>
 * </ul>
 *
 * <h2>Sample config JSON</h2>
 * 
 * <pre>
 * {
 *   "services": {
 *     "all":                  [{"concept":"167441","description":"PCR"}],
 *     "program:HIV":          [{"concept":"1000051","description":"Registration"},
 *                              {"concept":"856","description":"HIV Viral Load"}],
 *     "program:HIV:all":      [],
 *     "program:TB":           [{"concept":"162202","description":"GeneXpert"}],
 *     "program:TB:all":       [],
 *     "location:Mathare":     [{"concept":"32","description":"Malaria Smear"}],
 *     "location:Mathare:all": [],
 *     "age<5":                [{"concept":"32","description":"Malaria Smear"}],
 *     "visitAttribute:prisoner": [{"concept":"32","description":"Malaria Smear"}]
 *   },
 *   "commodities": {}
 * }
 * </pre>
 */
public abstract class BillingExemptions {

    private static final Log LOG = LogFactory.getLog(BillingExemptions.class);

    /** Suffix that marks a "wildcard" (all-services-exempt) key. */
    private static final String ALL_SUFFIX = ":all";

    /**
     * Map of exemption-key → set of exempt concept identifiers (IDs or UUIDs as
     * Strings). Keys follow the conventions documented in the class Javadoc.
     * <p>
     * For wildcard keys (those ending in {@code :all}), the value set is empty and
     * the concept-list check is intentionally skipped.
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
     * or UUID string) is exempt for {@code patient} according to the currently
     * loaded {@link #SERVICES} map.
     *
     * @param patient           the patient being billed
     * @param conceptIdentifier the concept ID or UUID of the service being billed
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
     * {@code patient} according to the currently loaded {@link #COMMODITIES} map.
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
            Set<String> concepts = entry.getValue();
            boolean isWildcard = isWildcardKey(key);

            /*
             * For non-wildcard keys: skip early if this concept is not listed.
             * For wildcard keys : skip the concept check entirely — ANY concept
             * is exempt when the patient meets the criteria.
             */
            if (!isWildcard && (concepts == null || !containsConcept(concepts, conceptIdentifier))) {
                continue;
            }

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
     * Returns {@code true} when the key represents a "wildcard" rule, meaning
     * every concept is exempt for patients who meet the associated criteria.
     * <p>
     * A key is a wildcard when it equals {@code "all"} or ends with {@code ":all"}
     * (e.g. {@code "program:HIV:all"}, {@code "location:Mathare:all"}).
     */
    private static boolean isWildcardKey(String key) {
        if (key == null) {
            return false;
        }
        String lk = key.trim().toLowerCase();
        return "all".equals(lk) || lk.endsWith(ALL_SUFFIX);
    }

    /**
     * Returns {@code true} when {@code conceptIdentifier} is present in
     * {@code conceptSet}. Comparison is case-insensitive and trims whitespace.
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
     * <p>
     * The {@code :all} suffix is stripped before routing so that
     * {@code "program:HIV:all"} is evaluated identically to {@code "program:HIV"}
     * from a <em>criteria</em> standpoint — the wildcard behaviour is handled
     * upstream in {@link #isExemptedInMap}.
     *
     * <ul>
     * <li>{@code "all"} / {@code "all:all"} → always {@code true}</li>
     * <li>{@code "program:<name>"} / {@code "program:<name>:all"}
     * → patient is actively enrolled in the named program</li>
     * <li>{@code "location:<name>"} / {@code "location:<name>:all"}
     * → provider's session location (or patient's active visit location)
     * matches the named location</li>
     * <li>{@code "age<N"} → patient age is 0 – (N-1)</li>
     * <li>{@code "visitAttribute:<value>"}
     * → patient's active visit has a matching attribute value</li>
     * </ul>
     */
    private static boolean patientMeetsCriteria(String key, Patient patient) {
        if (key == null) {
            return false;
        }

        // Strip the :all suffix so routing below works uniformly for both
        // "program:HIV" and "program:HIV:all"
        String effectiveKey = key.trim();
        if (effectiveKey.toLowerCase().endsWith(ALL_SUFFIX)) {
            effectiveKey = effectiveKey.substring(0, effectiveKey.length() - ALL_SUFFIX.length());
        }

        String normalizedKey = effectiveKey.toLowerCase();

        if ("all".equals(normalizedKey)) {
            return true;
        }

        if (normalizedKey.startsWith("program:")) {
            String programName = effectiveKey.substring("program:".length()).trim();
            return isEnrolledInProgram(patient, programName);
        }

        if (normalizedKey.startsWith("location:")) {
            String locationName = effectiveKey.substring("location:".length()).trim();
            return isAtLocation(patient, locationName);
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
            // Preserve original casing for the attribute value comparison
            String attributeValue = effectiveKey.substring("visitAttribute:".length()).trim();
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
            List<PatientProgram> patientPrograms = Context.getProgramWorkflowService()
                    .getPatientPrograms(patient, null, null, null, null, null, false);

            for (PatientProgram pp : patientPrograms) {
                boolean nameMatches = pp.getProgram() != null
                        && programName.equalsIgnoreCase(pp.getProgram().getName());
                boolean isActive = pp.getDateCompleted() == null; // null = still active
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
     * Returns {@code true} when the named location matches either:
     * <ol>
     * <li>The <strong>provider's current session location</strong> (preferred),
     * obtained via {@link Context#getUserContext()#getLocation()}; or</li>
     * <li>The <strong>location of the patient's active visit</strong>, used as a
     * fallback when no session location is set (e.g. during background
     * processing or API calls).</li>
     * </ol>
     *
     * @param patient      the patient being evaluated
     * @param locationName the location name from the exemption key
     *                     (case-insensitive)
     */
    private static boolean isAtLocation(Patient patient, String locationName) {
        try {
            // 1. Prefer the authenticated provider's session location
            Location sessionLocation = Context.getUserContext().getLocation();
            if (sessionLocation != null) {
                if (locationName.equalsIgnoreCase(sessionLocation.getName())) {
                    return true;
                }
                // Session location is set but doesn't match – no need to check visit
                return false;
            }

            // 2. Fallback: check the patient's active visit location
            List<Visit> activeVisits = Context.getVisitService().getActiveVisitsByPatient(patient);
            for (Visit visit : activeVisits) {
                if (visit.getLocation() != null
                        && locationName.equalsIgnoreCase(visit.getLocation().getName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error checking location for patient " + patient.getPatientId(), e);
        }
        return false;
    }

    /**
     * Returns {@code true} when the patient has an ongoing visit that contains at
     * least one {@link VisitAttribute} whose value equals {@code attributeValue}
     * (case-insensitive).
     *
     * <p>
     * Only visits that have not yet been stopped (i.e.,
     * {@code stopDatetime == null}) are considered active.
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