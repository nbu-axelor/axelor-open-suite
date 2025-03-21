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
package com.axelor.apps.base.service;

import com.axelor.app.internal.AppFilter;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.UnitConversion;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.db.repo.UnitConversionRepository;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Model;
import com.axelor.i18n.I18n;
import com.axelor.utils.template.TemplateMaker;
import com.google.inject.Inject;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public class UnitConversionServiceImpl implements UnitConversionService {

  private static final char TEMPLATE_DELIMITER = '$';
  private static final int DEFAULT_COEFFICIENT_SCALE = 12;

  protected AppBaseService appBaseService;

  protected UnitConversionRepository unitConversionRepo;

  @Inject
  public UnitConversionServiceImpl(
      AppBaseService appBaseService, UnitConversionRepository unitConversionRepo) {
    this.appBaseService = appBaseService;
    this.unitConversionRepo = unitConversionRepo;
  }

  /**
   * Convert a value from a unit to another
   *
   * @param startUnit The starting unit
   * @param endUnit The end unit
   * @param value The value to convert
   * @param scale The wanted scale of the result
   * @param product Optional, a product used for complex conversions. Input null if needless.
   * @return The converted value with the specified scale
   * @throws AxelorException
   */
  @Override
  public BigDecimal convert(
      Unit startUnit, Unit endUnit, BigDecimal value, int scale, Product product)
      throws AxelorException {
    List<UnitConversion> unitConversionList = fetchUnitConversionList();
    return convert(unitConversionList, startUnit, endUnit, value, scale, product, "Product");
  }

  protected BigDecimal convert(
      List<UnitConversion> unitConversionList,
      Unit startUnit,
      Unit endUnit,
      BigDecimal value,
      int scale,
      Model model,
      String nameInContext)
      throws AxelorException {
    if ((startUnit == null && endUnit == null)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(BaseExceptionMessage.UNIT_CONVERSION_3));
    }

    if (startUnit == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(BaseExceptionMessage.UNIT_CONVERSION_2));
    }

    if (endUnit == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(BaseExceptionMessage.UNIT_CONVERSION_4));
    }

    if (startUnit.equals(endUnit)) return value;
    else {
      try {
        BigDecimal coefficient =
            this.getCoefficient(unitConversionList, startUnit, endUnit, model, nameInContext);

        if (coefficient.signum() == 0) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(BaseExceptionMessage.COEFFICIENT_SHOULD_NOT_BE_ZERO),
              startUnit.getName(),
              endUnit.getName());
        }
        return value.multiply(coefficient).setScale(scale, RoundingMode.HALF_UP);
      } catch (IOException | ClassNotFoundException e) {
        TraceBackService.trace(e);
      }
    }
    return value;
  }

  /**
   * Get the conversion coefficient between two units from a conversion list. If the start unit and
   * the end unit can not be found in the list, then the units are swapped. If there still isn't any
   * result, an Exception is thrown.
   *
   * @param startUnit The start unit
   * @param endUnit The end unit
   * @param product Optional, a product used for complex conversions. Input null if needless.
   * @return A conversion coefficient to convert from startUnit to endUnit.
   * @throws AxelorException The required units are not found in the conversion list.
   * @throws CompilationFailedException
   * @throws ClassNotFoundException
   * @throws IOException
   */
  @Override
  public BigDecimal getCoefficient(Unit startUnit, Unit endUnit, Product product)
      throws AxelorException, CompilationFailedException, ClassNotFoundException, IOException {
    List<UnitConversion> unitConversionList = fetchUnitConversionList();
    return getCoefficient(unitConversionList, startUnit, endUnit, product, "Product");
  }

  protected BigDecimal getCoefficient(
      List<UnitConversion> unitConversionList,
      Unit startUnit,
      Unit endUnit,
      Model model,
      String nameInContext)
      throws AxelorException, CompilationFailedException, ClassNotFoundException, IOException {
    /* Looking for the start unit and the end unit in the unitConversionList to get the coefficient */
    TemplateMaker maker = null;
    if (model != null) {
      maker =
          new TemplateMaker(
              Optional.ofNullable(AuthUtils.getUser()).map(User::getActiveCompany).orElse(null)
                      != null
                  ? Optional.ofNullable(AuthUtils.getUser())
                      .map(User::getActiveCompany)
                      .map(Company::getTimezone)
                      .orElse(null)
                  : "",
              AppFilter.getLocale(),
              TEMPLATE_DELIMITER,
              TEMPLATE_DELIMITER);
      maker.setContext(model, nameInContext);
    }
    String eval = null;
    for (UnitConversion unitConversion : unitConversionList) {

      if (unitConversion.getStartUnit().equals(startUnit)
          && unitConversion.getEndUnit().equals(endUnit)) {
        if (unitConversion.getTypeSelect() == UnitConversionRepository.TYPE_COEFF) {
          return unitConversion.getCoef();
        } else if (model != null) {
          maker.setTemplate(unitConversion.getFormula());
          eval = maker.make();
          CompilerConfiguration conf = new CompilerConfiguration();
          ImportCustomizer customizer = new ImportCustomizer();
          customizer.addStaticStars("java.lang.Math");
          conf.addCompilationCustomizers(customizer);
          Binding binding = new Binding();
          GroovyShell shell = new GroovyShell(binding, conf);
          return new BigDecimal(shell.evaluate(eval).toString());
        }
      }

      /* The endUnit become the start unit and the startUnit become the end unit */

      if (unitConversion.getStartUnit().equals(endUnit)
          && unitConversion.getEndUnit().equals(startUnit)) {
        if (unitConversion.getTypeSelect() == UnitConversionRepository.TYPE_COEFF
            && unitConversion.getCoef().compareTo(BigDecimal.ZERO) != 0) {
          return BigDecimal.ONE.divide(
              unitConversion.getCoef(), DEFAULT_COEFFICIENT_SCALE, RoundingMode.HALF_UP);
        } else if (model != null) {
          maker.setTemplate(unitConversion.getFormula());
          eval = maker.make();
          CompilerConfiguration conf = new CompilerConfiguration();
          ImportCustomizer customizer = new ImportCustomizer();
          customizer.addStaticStars("java.lang.Math");
          conf.addCompilationCustomizers(customizer);
          Binding binding = new Binding();
          GroovyShell shell = new GroovyShell(binding, conf);
          BigDecimal result = new BigDecimal(shell.evaluate(eval).toString());
          if (result.compareTo(BigDecimal.ZERO) != 0) {
            return BigDecimal.ONE.divide(result, DEFAULT_COEFFICIENT_SCALE, RoundingMode.HALF_UP);
          }
        }
      }
    }
    /* If there is no startUnit and endUnit in the UnitConversion list so we throw an exception */
    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(BaseExceptionMessage.UNIT_CONVERSION_1),
        startUnit.getName(),
        endUnit.getName());
  }

  protected List<UnitConversion> fetchUnitConversionList() {
    return unitConversionRepo
        .all()
        .filter("self.entitySelect = :entitySelect")
        .bind("entitySelect", UnitConversionRepository.ENTITY_ALL)
        .fetch();
  }
}
