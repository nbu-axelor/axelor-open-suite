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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountEquiv;
import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.base.service.tax.FiscalPositionServiceImpl;
import com.axelor.apps.base.service.tax.TaxService;
import com.google.inject.Inject;

public class FiscalPositionAccountServiceImpl extends FiscalPositionServiceImpl
    implements FiscalPositionAccountService {

  @Inject
  public FiscalPositionAccountServiceImpl(TaxService taxService) {
    super(taxService);
  }

  @Override
  public Account getAccount(FiscalPosition fiscalPosition, Account account) {

    if (fiscalPosition != null && fiscalPosition.getAccountEquivList() != null) {
      for (AccountEquiv accountEquiv : fiscalPosition.getAccountEquivList()) {

        if (accountEquiv.getFromAccount().equals(account) && accountEquiv.getToAccount() != null) {
          return accountEquiv.getToAccount();
        }
      }
    }

    return account;
  }
}
