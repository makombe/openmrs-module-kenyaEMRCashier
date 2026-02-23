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
package org.openmrs.module.kenyaemr.cashier.api.model;

import java.math.BigDecimal;

/**
 * DTO for bill totals aggregated by status (for reporting/performance).
 * Totals are computed at the database layer from non-voided bill line items.
 */
public class BillSummary {

	private BigDecimal totalBills = BigDecimal.ZERO;
	private BigDecimal pendingBills = BigDecimal.ZERO;
	private BigDecimal paidBills = BigDecimal.ZERO;
	private BigDecimal exemptedBills = BigDecimal.ZERO;

	public BillSummary() {
	}

	public BigDecimal getTotalBills() {
		return totalBills != null ? totalBills : BigDecimal.ZERO;
	}

	public void setTotalBills(BigDecimal totalBills) {
		this.totalBills = totalBills != null ? totalBills : BigDecimal.ZERO;
	}

	public BigDecimal getPendingBills() {
		return pendingBills != null ? pendingBills : BigDecimal.ZERO;
	}

	public void setPendingBills(BigDecimal pendingBills) {
		this.pendingBills = pendingBills != null ? pendingBills : BigDecimal.ZERO;
	}

	public BigDecimal getPaidBills() {
		return paidBills != null ? paidBills : BigDecimal.ZERO;
	}

	public void setPaidBills(BigDecimal paidBills) {
		this.paidBills = paidBills != null ? paidBills : BigDecimal.ZERO;
	}

	public BigDecimal getExemptedBills() {
		return exemptedBills != null ? exemptedBills : BigDecimal.ZERO;
	}

	public void setExemptedBills(BigDecimal exemptedBills) {
		this.exemptedBills = exemptedBills != null ? exemptedBills : BigDecimal.ZERO;
	}
}
