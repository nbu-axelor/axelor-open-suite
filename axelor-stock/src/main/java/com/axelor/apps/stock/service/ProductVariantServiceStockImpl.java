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
package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.ProductVariantRepository;
import com.axelor.apps.base.service.ProductVariantServiceImpl;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class ProductVariantServiceStockImpl extends ProductVariantServiceImpl {

  @Inject
  public ProductVariantServiceStockImpl(
      ProductRepository productRepo, ProductVariantRepository productVariantRepo) {
    super(productRepo, productVariantRepo);
  }

  @Override
  public Product copyAdditionalFields(Product product, Product productModel) {
    product = super.copyAdditionalFields(product, productModel);
    if (Beans.get(AppBaseService.class).isApp("stock")) {
      product.setUsedInDEB(productModel.getUsedInDEB());
      product.setCustomsCodeNomenclature(productModel.getCustomsCodeNomenclature());
      product.setStockManaged(productModel.getStockManaged());
      product.setControlOnReceipt(productModel.getControlOnReceipt());
      product.setTrackingNumberConfiguration(productModel.getTrackingNumberConfiguration());
    }
    return product;
  }
}
