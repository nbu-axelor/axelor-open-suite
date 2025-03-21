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
package com.axelor.apps.production.web;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.production.db.BillOfMaterialImportLine;
import com.axelor.apps.production.db.TempBomTree;
import com.axelor.apps.production.service.BillOfMaterialService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class ProductController {

  public void openProductTree(ActionRequest request, ActionResponse response) {

    try {
      Product product = request.getContext().asType(Product.class);
      product = Beans.get(ProductRepository.class).find(product.getId());

      TempBomTree tempBomTree =
          Beans.get(BillOfMaterialService.class)
              .generateTree(product.getDefaultBillOfMaterial(), true);

      response.setView(
          ActionView.define(I18n.get("Bill of materials"))
              .model(TempBomTree.class.getName())
              .add("tree", "bill-of-material-tree")
              .context("_tempBomTreeId", tempBomTree.getId())
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void bomImportLineProductOnNew(ActionRequest request, ActionResponse response) {
    if (request.getContext().getParent() != null) {
      BillOfMaterialImportLine billOfMaterialImportLine =
          request.getContext().getParent().asType(BillOfMaterialImportLine.class);

      response.setValue("code", billOfMaterialImportLine.getCode());
      response.setValue("name", billOfMaterialImportLine.getName());
      response.setValue("productTypeSelect", ProductRepository.PRODUCT_TYPE_STORABLE);
      response.setValue(
          "createdFromBOMImportId", billOfMaterialImportLine.getBillOfMaterialImport().getId());
    }
  }
}
