package org.openmrs.module.kenyaemr.cashier.api.search;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Concept;
import org.openmrs.module.kenyaemr.cashier.api.base.entity.search.BaseDataTemplateSearch;
import org.openmrs.module.kenyaemr.cashier.api.model.BillableService;

public class BillableServiceSearch extends BaseDataTemplateSearch<BillableService> {
    private List<Concept> concepts;

    public BillableServiceSearch() {
        this(new BillableService(), false);
    }

    public BillableServiceSearch(BillableService template) {
        this(template, false);
    }

    public BillableServiceSearch(BillableService template, Boolean includeRetired) {
        super(template, includeRetired);
    }
    
    public void setConcepts(List<Concept> concepts) {
        this.concepts = concepts;
    }

    public List<Concept> getConcepts() {
        return concepts;
    }


    @Override
    public void updateCriteria(Criteria criteria) {
        super.updateCriteria(criteria);

        BillableService billableService = getTemplate();

        if (StringUtils.isNotBlank(billableService.getName())) {
            criteria.add(Restrictions.eq("name", billableService.getName()).ignoreCase());
        }

        if (billableService.getServiceStatus() != null) {
            criteria.add(Restrictions.eq("serviceStatus", billableService.getServiceStatus()));
        }

        if (billableService.getServiceCategory() != null) {
            criteria.add(Restrictions.eq("serviceCategory", billableService.getServiceCategory()));
        }

        if (billableService.getServiceType() != null) {
            criteria.add(Restrictions.eq("serviceType", billableService.getServiceType()));
        }

       if (concepts != null && !concepts.isEmpty()) {
            criteria.add(Restrictions.in("concept", concepts));
        } else if (billableService.getConcept() != null) {
            criteria.add(Restrictions.eq("concept", billableService.getConcept()));
        }

        if (billableService.getStockItem() != null) {
            criteria.add(Restrictions.eq("stockItem", billableService.getStockItem()));
        }
    }
}