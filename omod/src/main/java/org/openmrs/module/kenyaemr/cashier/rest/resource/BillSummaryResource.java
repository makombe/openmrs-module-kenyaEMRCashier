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
package org.openmrs.module.kenyaemr.cashier.rest.resource;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemr.cashier.api.IBillService;
import org.openmrs.module.kenyaemr.cashier.api.model.Bill;
import org.openmrs.module.kenyaemr.cashier.api.model.BillStatus;
import org.openmrs.module.kenyaemr.cashier.api.model.BillSummary;
import org.openmrs.module.kenyaemr.cashier.api.search.BillSearch;
import org.openmrs.module.kenyaemr.cashier.rest.controller.base.CashierResourceController;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * REST resource for bill summary (totals by status).
 * Exposes GET only; aggregation is performed at the database layer.
 * Response follows OpenMRS REST: { "results": [ { "totalBills", "pendingBills", "paidBills", "exemptedBills" } ], "links": [...] }
 */
@Resource(name = RestConstants.VERSION_1 + CashierResourceController.KENYAEMR_CASHIER_NAMESPACE + "/bill-summary",
		supportedClass = BillSummary.class, supportedOpenmrsVersions = { "2.0 - 2.*" })
public class BillSummaryResource extends DelegatingCrudResource<BillSummary> {

	@Override
	public BillSummary getByUniqueId(String uniqueId) {
		throw new ResourceDoesNotSupportOperationException("Bill summary does not support get by uuid");
	}

	@Override
	public BillSummary newDelegate() {
		return new BillSummary();
	}

	@Override
	public BillSummary save(BillSummary delegate) {
		throw new ResourceDoesNotSupportOperationException("Bill summary is read-only");
	}

	@Override
	protected void delete(BillSummary delegate, String reason, RequestContext context) {
		throw new ResourceDoesNotSupportOperationException("Bill summary is read-only");
	}

	@Override
	public void purge(BillSummary delegate, RequestContext context) {
		throw new ResourceDoesNotSupportOperationException("Bill summary is read-only");
	}

	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("totalBills");
		description.addProperty("pendingBills");
		description.addProperty("paidBills");
		description.addProperty("exemptedBills");
		return description;
	}

	@Override
	protected PageableResult doSearch(RequestContext context) {
		BillSearch billSearch = buildBillSearchFromContext(context);
		IBillService service = Context.getService(IBillService.class);
		BillSummary summary = service.getBillSummary(billSearch);
		List<BillSummary> results = Collections.singletonList(summary);
		return new AlreadyPaged<>(context, results, false);
	}

	private BillSearch buildBillSearchFromContext(RequestContext context) {
		// Use same parameter source as BillResource so summary and list use identical criteria
		String patientUuid = context.getRequest().getParameter("patientUuid");
		String statusParam = context.getRequest().getParameter("status");
		String createdOnOrAfterStr = context.getRequest().getParameter("createdOnOrAfter");
		String createdOnOrBeforeStr = context.getRequest().getParameter("createdOnOrBefore");
		String includeVoidedBillsStr = context.getRequest().getParameter("includeVoidedBills");
		String includeClosedBillsStr = context.getRequest().getParameter("includeClosedBills");

		Patient patient = Strings.isNotEmpty(patientUuid)
				? Context.getPatientService().getPatientByUuid(patientUuid)
				: null;
		BillStatus billStatus = Strings.isNotEmpty(statusParam) ? BillStatus.valueOf(statusParam.toUpperCase()) : null;

		// Parse dates the same way as BillResource (raw conversion); start/end-of-day is applied in service to match BillSearch
		Date createdOnOrAfter = StringUtils.isNotBlank(createdOnOrAfterStr)
				? (Date) ConversionUtil.convert(createdOnOrAfterStr, Date.class)
				: null;
		Date createdOnOrBefore = StringUtils.isNotBlank(createdOnOrBeforeStr)
				? (Date) ConversionUtil.convert(createdOnOrBeforeStr, Date.class)
				: null;

		boolean includeVoidedBills = Strings.isNotEmpty(includeVoidedBillsStr)
				&& Boolean.parseBoolean(includeVoidedBillsStr);
		boolean includeClosedBills = Strings.isEmpty(includeClosedBillsStr)
				|| Boolean.parseBoolean(includeClosedBillsStr);

		Bill template = new Bill();
		template.setPatient(patient);
		template.setStatus(billStatus);
		return new BillSearch(template, createdOnOrAfter, createdOnOrBefore, includeVoidedBills, includeClosedBills);
	}
}
