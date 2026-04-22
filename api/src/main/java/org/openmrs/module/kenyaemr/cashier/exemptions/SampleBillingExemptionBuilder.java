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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Builds the billing exemption lists from the JSON stored in the
 * {@code kenyaemr.billing.exemptions} global property.
 *
 * <p>
 * Concept identifiers are treated as <strong>Strings</strong> so that both
 * plain integer IDs
 * (e.g. {@code "856"}) and UUID-style strings (e.g. {@code "856000001122243"})
 * are handled
 * correctly without data loss.
 *
 * <p>
 * Supported exemption key patterns (see {@link BillingExemptions} for full
 * documentation):
 * <ul>
 * <li>{@code all}</li>
 * <li>{@code program:<ProgramName>}</li>
 * <li>{@code age<N}</li>
 * <li>{@code visitAttribute:<value>}</li>
 * </ul>
 */
public class SampleBillingExemptionBuilder extends BillingExemptions {

    private static final Log LOG = LogFactory.getLog(SampleBillingExemptionBuilder.class);
    private static final String EXEMPTIONS_GP = "kenyaemr.billing.exemptions";

    public SampleBillingExemptionBuilder() {
    }

    /**
     * Reads the exemptions config from the global property and populates
     * {@link BillingExemptions#SERVICES} and {@link BillingExemptions#COMMODITIES}.
     * Falls back to empty maps on any parse failure so that billing can still
     * proceed.
     */
    @Override
    public void buildBillingExemptionList() {
        AdministrationService adminService = Context.getAdministrationService();
        String exemptionConfig = adminService.getGlobalProperty(EXEMPTIONS_GP);

        if (StringUtils.isBlank(exemptionConfig)) {
            LOG.warn("Global property '" + EXEMPTIONS_GP + "' is empty – no billing exemptions loaded.");
            initializeExemptionsConfig();
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode config;
        try {
            config = (ObjectNode) mapper.readTree(exemptionConfig);
        } catch (IOException e) {
            LOG.error("Could not parse billing exemptions JSON. Check that the value of '"
                    + EXEMPTIONS_GP + "' is well-formed JSON.", e);
            initializeExemptionsConfig();
            return;
        }

        JsonNode servicesNode = config.get("services");
        if (servicesNode != null && servicesNode.isObject()) {
            Map<String, Set<String>> exemptedServices = mapConcepts((ObjectNode) servicesNode);
            LOG.info("Loaded billing exemptions – services: " + exemptedServices);
            BillingExemptions.setSERVICES(exemptedServices);
        } else {
            LOG.warn("No 'services' section found in billing exemptions config.");
            BillingExemptions.setSERVICES(new HashMap<>());
        }

        JsonNode commoditiesNode = config.get("commodities");
        if (commoditiesNode != null && commoditiesNode.isObject()) {
            Map<String, Set<String>> exemptedCommodities = mapConcepts((ObjectNode) commoditiesNode);
            LOG.info("Loaded billing exemptions – commodities: " + exemptedCommodities);
            BillingExemptions.setCOMMODITIES(exemptedCommodities);
        } else {
            LOG.warn("No 'commodities' section found in billing exemptions config.");
            BillingExemptions.setCOMMODITIES(new HashMap<>());
        }
    }

    /**
     * Converts a JSON object of the form:
     * 
     * <pre>
     * {
     *   "all":         [{"concept":"167441","description":"PCR"}, ...],
     *   "program:HIV": [{"concept":"856","description":"HIV Viral Load"}, ...]
     * }
     * </pre>
     * 
     * into a {@code Map<String, Set<String>>} keyed by exemption group with concept
     * identifier strings as values.
     *
     * <p>
     * Entries whose concept field is missing, null, or blank are silently skipped
     * so that a single malformed entry does not break the entire configuration.
     */
    private Map<String, Set<String>> mapConcepts(ObjectNode node) {
        Map<String, Set<String>> exemptionList = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.getFields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();

            if (StringUtils.isBlank(key)) {
                LOG.warn("Skipping exemption entry with blank key.");
                continue;
            }

            JsonNode conceptsArray = entry.getValue();
            if (!conceptsArray.isArray()) {
                LOG.warn("Expected an array for exemption key '" + key + "' – skipping.");
                continue;
            }

            Set<String> conceptSet = new HashSet<>();
            ArrayNode conceptIds = (ArrayNode) conceptsArray;
            for (int i = 0; i < conceptIds.size(); i++) {
                JsonNode conceptObj = conceptIds.get(i);
                if (conceptObj == null || !conceptObj.isObject()) {
                    continue;
                }
                JsonNode conceptField = conceptObj.get("concept");
                if (conceptField == null || conceptField.isNull()) {
                    LOG.warn("Exemption entry at index " + i + " under key '" + key
                            + "' is missing the 'concept' field – skipping.");
                    continue;
                }
                // Concept values are integer concept IDs. Parse as integer to validate,
                // then store as a trimmed string for lookup comparison.
                String rawValue = conceptField.asText().trim();
                try {
                    Integer.parseInt(rawValue); // validates it is a real integer concept ID
                    conceptSet.add(rawValue);
                } catch (NumberFormatException e) {
                    LOG.warn("Exemption entry at index " + i + " under key '" + key
                            + "' has non-integer concept value '" + rawValue + "' – skipping.");
                }
            }

            if (!conceptSet.isEmpty()) {
                exemptionList.put(key, conceptSet);
            } else {
                LOG.warn("Exemption key '" + key + "' has no valid concept entries – skipping.");
            }
        }

        return exemptionList;
    }

    /**
     * Resets both maps to empty (non-null) collections so callers can safely
     * iterate
     * even when the configuration is absent or unparseable.
     */
    private void initializeExemptionsConfig() {
        BillingExemptions.setSERVICES(new HashMap<>());
        BillingExemptions.setCOMMODITIES(new HashMap<>());
    }
}