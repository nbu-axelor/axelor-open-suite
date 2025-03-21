/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.service.custom;

import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.AccountingReportConfigLine;
import com.axelor.apps.account.db.AccountingReportValue;
import com.axelor.apps.account.db.AnalyticAccount;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public interface AccountingReportValuePercentageService {
  void createPercentageValue(
      AccountingReport accountingReport,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      AccountingReportConfigLine groupColumn,
      Map<String, Map<String, AccountingReportValue>> valuesMapByColumn,
      Map<String, Map<String, AccountingReportValue>> valuesMapByLine,
      Set<Company> companySet,
      AnalyticAccount configAnalyticAccount,
      LocalDate startDate,
      LocalDate endDate,
      String parentTitle,
      int analyticCounter)
      throws AxelorException;
}
