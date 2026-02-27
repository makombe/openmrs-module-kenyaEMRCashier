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
package org.openmrs.module.kenyaemr.cashier.rest.controller;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemr.cashier.api.IBillService;
import org.openmrs.module.kenyaemr.cashier.api.model.Bill;
import org.openmrs.module.kenyaemr.cashier.api.util.PrivilegeConstants;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controller to manage the Receipt Generation Page
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/cashier/receipt")
public class ReceiptController extends BaseRestController {

	@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<byte[]> get(
	        @RequestParam(value = "billId", required = false) Integer billId,
	        @RequestParam(value = "lineItemUuids", required = false) String lineItemUuidsParam,
	        @RequestParam(value = "paymentsUuids", required = false) String paymentUuidsParam) throws IOException {

		List<String> lineItemUuids = parseLineItemUuids(lineItemUuidsParam);
		List<String> paymentUuids = parsePaymentUuids(paymentUuidsParam);
		return generateReceiptResponse(billId, lineItemUuids, paymentUuids);
	}

	@RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<byte[]> post(@RequestBody ReceiptRequest request) throws IOException {
		if (request == null || request.getBillId() == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return generateReceiptResponse(request.getBillId(), request.getLineItemUuids(), request.getPaymentUuids());
	}

	private ResponseEntity<byte[]> generateReceiptResponse(Integer billId, List<String> lineItemUuids) {
		return generateReceiptResponse(billId, lineItemUuids, null);
	}

	private ResponseEntity<byte[]> generateReceiptResponse(Integer billId, List<String> lineItemUuids, List<String> paymentUuids) {
		IBillService service = Context.getService(IBillService.class);
		Bill bill = service.getById(billId);

		if (bill == null) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		boolean hasLineItemFilter = (lineItemUuids != null && !lineItemUuids.isEmpty());
		boolean hasPaymentFilter = (paymentUuids != null && !paymentUuids.isEmpty());

		File file;
		if (!hasLineItemFilter && !hasPaymentFilter) {
			file = service.downloadBillReceipt(bill);
		} else if (hasLineItemFilter && !hasPaymentFilter) {
			file = service.downloadBillReceipt(bill, lineItemUuids);
		} else {
			// When payment UUIDs are specified, allow lineItemUuids to be null/empty to include all items
			file = service.downloadBillReceipt(bill, lineItemUuids, paymentUuids);
		}

		if (file != null && file.exists()) {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
			headers.setContentDispositionFormData("attachment", file.getName());
			headers.add("Access-Control-Allow-Origin", "*");

			try {
				byte[] fileContent = Files.readAllBytes(file.toPath());
				return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
			} catch (IOException e) {
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}

	/**
	 * Parses the optional lineItemUuids request parameter into a list of UUIDs.
	 * Accepts a single UUID or a comma-separated list.
	 */
	private List<String> parseLineItemUuids(String lineItemUuidsParam) {
		if (StringUtils.isBlank(lineItemUuidsParam)) {
			return Collections.emptyList();
		}

		String[] tokens = lineItemUuidsParam.split(",");
		List<String> uuids = new ArrayList<String>();
		for (String token : tokens) {
			if (StringUtils.isNotBlank(token)) {
				uuids.add(token.trim());
			}
		}
		return uuids;
	}

	/**
	 * Parses the optional paymentUuids request parameter into a list of UUIDs.
	 * Accepts a single UUID or a comma-separated list.
	 */
	private List<String> parsePaymentUuids(String paymentUuidsParam) {
		if (StringUtils.isBlank(paymentUuidsParam)) {
			return Collections.emptyList();
		}

		String[] tokens = paymentUuidsParam.split(",");
		List<String> uuids = new ArrayList<String>();
		for (String token : tokens) {
			if (StringUtils.isNotBlank(token)) {
				uuids.add(token.trim());
			}
		}
		return uuids;
	}

	/**
	 * Request body model for generating a receipt, allowing a large list of line item UUIDs
	 * to be sent in the JSON payload instead of the query string.
	 */
	public static class ReceiptRequest {
		private Integer billId;
		private List<String> lineItemUuids;
		private List<String> paymentUuids;

		public Integer getBillId() {
			return billId;
		}

		public void setBillId(Integer billId) {
			this.billId = billId;
		}

		public List<String> getLineItemUuids() {
			return lineItemUuids;
		}

		public void setLineItemUuids(List<String> lineItemUuids) {
			this.lineItemUuids = lineItemUuids;
		}

		public List<String> getPaymentUuids() {
			return paymentUuids;
		}

		public void setPaymentUuids(List<String> paymentUuids) {
			this.paymentUuids = paymentUuids;
		}
	}

	private boolean validateBill(Integer billId, Bill bill, HttpServletResponse response) throws IOException {
		if (bill == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find bill with bill Id '"
			        + billId + "'");

			return false;
		}

		if (bill.isReceiptPrinted() && !Context.hasPrivilege(PrivilegeConstants.REPRINT_RECEIPT)) {
			if (StringUtils.isEmpty(bill.getReceiptNumber())) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to reprint receipt '"
				        + bill.getReceiptNumber() + "'");
			} else {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to reprint bill '"
				        + billId + "'");
			}

			return false;
		}

		return true;
	}
}
