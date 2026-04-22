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
package org.openmrs.module.kenyaemr.cashier.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.module.kenyaemr.cashier.api.model.Bill;
import org.openmrs.module.kenyaemr.cashier.api.model.BillLineItem;
import org.openmrs.module.kenyaemr.cashier.api.model.BillStatus;
import org.openmrs.module.kenyaemr.cashier.exemptions.BillingExemptions;

import java.util.List;

/**
 * Utility class responsible for evaluating billing exemptions and applying them
 * to
 * {@link BillLineItem}s before a {@link Bill} is persisted.
 *
 * <h2>Usage</h2>
 * Call {@link #applyExemptions(Bill)} immediately before saving a new bill (or
 * when new
 * line items are added to an existing bill). Each eligible line item whose
 * concept appears
 * in the loaded exemption configuration will have its {@code paymentStatus} set
 * to
 * {@link BillStatus#EXEMPTED} automatically.
 *
 * <h2>Design notes</h2>
 * <ul>
 * <li>Voided items are never modified.</li>
 * <li>Items that are already {@code PAID} or {@code POSTED} are left untouched
 * so that
 * partial-payment scenarios are not disrupted.</li>
 * <li>Both service concepts (via {@link BillLineItem#getBillableService()}) and
 * drug/
 * commodity concepts (via {@link BillLineItem#getItem()}) are evaluated.</li>
 * </ul>
 */
public class ExemptionEvaluator {

    private static final Log LOG = LogFactory.getLog(ExemptionEvaluator.class);

    // Prevent instantiation – this is a pure-utility class
    private ExemptionEvaluator() {
    }

    /**
     * Evaluates every {@link BillLineItem} on the supplied {@link Bill} against the
     * currently loaded {@link BillingExemptions} configuration and marks qualifying
     * items as {@link BillStatus#EXEMPTED}.
     *
     * @param bill the bill whose line items should be evaluated; must not be
     *             {@code null}
     * @return the number of line items that were newly exempted during this call
     */
    public static int applyExemptions(Bill bill) {
        if (bill == null) {
            throw new IllegalArgumentException("Bill must not be null");
        }

        Patient patient = bill.getPatient();
        if (patient == null) {
            LOG.warn("Bill has no patient – skipping exemption evaluation.");
            return 0;
        }

        List<BillLineItem> lineItems = bill.getLineItems();
        if (lineItems == null || lineItems.isEmpty()) {
            return 0;
        }

        int exemptedCount = 0;
        for (BillLineItem item : lineItems) {
            if (item == null || item.getVoided()) {
                continue;
            }

            // Do not downgrade items that are already fully settled
            BillStatus currentStatus = item.getPaymentStatus();
            if (currentStatus == BillStatus.PAID || currentStatus == BillStatus.POSTED || currentStatus == BillStatus.EXEMPTED) {
                continue;
            }

            // Resolve the concept identifier for this line item
            String conceptIdentifier = resolveConceptIdentifier(item);
            if (conceptIdentifier == null) {
                continue;
            }

            boolean exempt = isLineItemExempted(item, patient, conceptIdentifier);
            if (exempt && currentStatus != BillStatus.EXEMPTED) {
                LOG.info(String.format(
                        "Exempting line item '%s' (concept: %s) for patient %d",
                        getItemName(item), conceptIdentifier, patient.getPatientId()));
                item.setPaymentStatus(BillStatus.EXEMPTED);
                exemptedCount++;
            }
        }

        if (exemptedCount > 0) {
            LOG.info(String.format("%d line item(s) exempted on bill for patient %d",
                    exemptedCount, patient.getPatientId()));
            // Re-sync the bill status in case all items are now exempted
            bill.synchronizeBillStatus();
        }

        return exemptedCount;
    }

    /**
     * Returns {@code true} when the line item's concept is exempt for the patient.
     * Checks both {@link BillingExemptions#SERVICES} (for billable services) and
     * {@link BillingExemptions#COMMODITIES} (for stock items/drugs).
     */
    private static boolean isLineItemExempted(BillLineItem item, Patient patient, String conceptIdentifier) {
        if (item.getBillableService() != null) {
            return BillingExemptions.isServiceExempted(patient, conceptIdentifier);
        }

        // Stock item / commodity → check COMMODITIES map first, fall back to SERVICES
        if (item.getItem() != null) {
            if (BillingExemptions.isCommodityExempted(patient, conceptIdentifier)) {
                return true;
            }
            // Some sites store commodities inside SERVICES too – honour that
            return BillingExemptions.isServiceExempted(patient, conceptIdentifier);
        }

        return false;
    }

    /**
     * Extracts the integer concept ID (as a String) from the line item so it can be
     * compared
     * against the values stored in the exemption config, which are also integer
     * concept IDs.
     * Returns {@code null} when the line item has no resolvable concept.
     */
    private static String resolveConceptIdentifier(BillLineItem item) {
        if (item.getBillableService() != null && item.getBillableService().getConcept() != null) {
            return resolveFromConcept(item.getBillableService().getConcept());
        }
        if (item.getItem() != null && item.getItem().getConcept() != null) {
            return resolveFromConcept(item.getItem().getConcept());
        }
        return null;
    }

    /**
     * Returns the concept's integer ID as a String.
     * The exemption config stores concept IDs as plain integers (e.g. "856",
     * "167441"),
     * so we always resolve by ID – never by UUID.
     */
    private static String resolveFromConcept(Concept concept) {
        return concept.getId() != null ? concept.getId().toString() : null;
    }

    /**
     * Returns a human-readable name for the line item, used only for logging.
     */
    private static String getItemName(BillLineItem item) {
        if (item.getBillableService() != null) {
            return item.getBillableService().getName();
        }
        if (item.getItem() != null) {
            return item.getItem().getCommonName();
        }
        return "<unknown>";
    }
}