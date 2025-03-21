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
package com.axelor.apps.businessproject.service.projectgenerator.factory;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.businessproject.exception.BusinessProjectExceptionMessage;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.projectgenerator.ProjectGeneratorFactory;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.service.app.AppProjectService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;

public class ProjectGeneratorFactoryAlone implements ProjectGeneratorFactory {

  protected ProjectBusinessService projectBusinessService;
  protected ProjectRepository projectRepository;
  protected SequenceService sequenceService;
  protected AppProjectService appProjectService;

  @Inject
  public ProjectGeneratorFactoryAlone(
      ProjectBusinessService projectBusinessService,
      ProjectRepository projectRepository,
      SequenceService sequenceService,
      AppProjectService appProjectService) {
    this.projectBusinessService = projectBusinessService;
    this.projectRepository = projectRepository;
    this.sequenceService = sequenceService;
    this.appProjectService = appProjectService;
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public Project create(SaleOrder saleOrder) {
    Project project = projectBusinessService.generateProject(saleOrder);
    project.setIsBusinessProject(true);
    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      saleOrderLine.setProject(project);
    }
    try {
      if (!appProjectService.getAppProject().getGenerateProjectSequence()) {
        project.setCode(sequenceService.getDraftSequenceNumber(project));
      }
    } catch (AxelorException e) {
      TraceBackService.trace(e);
    }
    return projectRepository.save(project);
  }

  @Override
  public ActionViewBuilder fill(Project project, SaleOrder saleOrder, LocalDateTime localDateTime)
      throws AxelorException {
    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(BusinessProjectExceptionMessage.FACTORY_FILL_WITH_PROJECT_ALONE));
  }
}
