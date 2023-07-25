/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2023 Axelor (<http://axelor.com>).
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
package com.axelor.apps.hr.service.expense;

import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductFamily;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.Expense;
import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.db.repo.ExpenseLineRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ExpenseLineServiceImpl implements ExpenseLineService {

  protected ExpenseLineRepository expenseLineRepository;

  @Inject
  public ExpenseLineServiceImpl(ExpenseLineRepository expenseLineRepository) {
    this.expenseLineRepository = expenseLineRepository;
  }

  @Override
  public List<ExpenseLine> getExpenseLineList(Expense expense) {
    List<ExpenseLine> expenseLineList = new ArrayList<>();
    if (expense.getGeneralExpenseLineList() != null) {
      expenseLineList.addAll(expense.getGeneralExpenseLineList());
    }
    if (expense.getKilometricExpenseLineList() != null) {
      expenseLineList.addAll(expense.getKilometricExpenseLineList());
    }
    return expenseLineList;
  }

  @Override
  public void completeExpenseLines(Expense expense) {
    List<ExpenseLine> expenseLineList =
        expenseLineRepository
            .all()
            .filter("self.expense.id = :_expenseId")
            .bind("_expenseId", expense.getId())
            .fetch();
    List<ExpenseLine> kilometricExpenseLineList = expense.getKilometricExpenseLineList();
    List<ExpenseLine> generalExpenseLineList = expense.getGeneralExpenseLineList();

    // removing expense from one O2M also remove the link
    for (ExpenseLine expenseLine : expenseLineList) {
      if (!kilometricExpenseLineList.contains(expenseLine)
          && !generalExpenseLineList.contains(expenseLine)) {
        expenseLine.setExpense(null);
        expenseLineRepository.remove(expenseLine);
      }
    }

    // adding expense in one O2M also add the link
    if (kilometricExpenseLineList != null) {
      for (ExpenseLine kilometricLine : kilometricExpenseLineList) {
        if (!expenseLineList.contains(kilometricLine)) {
          kilometricLine.setExpense(expense);
        }
      }
    }
    if (generalExpenseLineList != null) {
      for (ExpenseLine generalExpenseLine : generalExpenseLineList) {
        if (!expenseLineList.contains(generalExpenseLine)) {
          generalExpenseLine.setExpense(expense);
        }
      }
    }
  }

  @Override
  public ExpenseLine getTotalTaxFromProductAndTotalAmount(ExpenseLine expenseLine) {
    AccountManagement accountManagement = null;
    Product product = expenseLine.getExpenseProduct();
    if(product != null){
      accountManagement = product.getAccountManagementList().stream()
              .filter(it -> (it.getPurchaseTax() != null))
              .findFirst()
              .orElse(null);
      if (accountManagement == null) {
        ProductFamily productFamily = expenseLine.getExpenseProduct().getProductFamily();
        if(productFamily != null){
          accountManagement = productFamily.getAccountManagementList().stream()
                  .filter(it -> (it.getPurchaseTax() != null))
                  .findFirst()
                  .orElse(null);
        }

      }
    }
    if (accountManagement != null) {
      Tax tax = accountManagement.getPurchaseTax();
      BigDecimal value =
              expenseLine
                      .getTotalAmount()
                      .divide(
                              (tax.getActiveTaxLine()
                                      .getValue()
                                      .add(BigDecimal.valueOf(100))
                                      .divide(
                                              BigDecimal.valueOf(100),
                                              AppBaseService.DEFAULT_NB_DECIMAL_DIGITS,
                                              RoundingMode.HALF_UP)),
                              AppBaseService.DEFAULT_NB_DECIMAL_DIGITS,
                              RoundingMode.HALF_UP);
      expenseLine.setTotalTax(value);
    }else{
      expenseLine.setTotalTax(BigDecimal.ZERO);
    }
    return expenseLine;
  }
}
