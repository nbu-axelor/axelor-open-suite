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
package com.axelor.apps.businessproject.service.batch;

import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.businessproject.exception.BusinessProjectExceptionMessage;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class BatchTransitionBetweenPaidStatusService extends AbstractBatch {

  protected ProjectRepository projectRepository;
  protected ProjectBusinessService projectBusinessService;

  @Inject
  public BatchTransitionBetweenPaidStatusService(
      ProjectRepository projectRepository, ProjectBusinessService projectBusinessService) {
    this.projectRepository = projectRepository;
    this.projectBusinessService = projectBusinessService;
  }

  @Override
  protected void process() {
    int offset = 0;
    List<Project> projectList;
    Query<Project> projectQuery =
        projectRepository
            .all()
            .order("id")
            .filter(
                "self.isBusinessProject = true AND self.projectStatus.isCompleted = true AND self.fromDate >= :fromDate")
            .bind(
                "fromDate",
                Optional.ofNullable(batch.getBusinessProjectBatch().getFromDate())
                    .orElse(LocalDate.EPOCH));
    while (!(projectList = projectQuery.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      findBatch();
      for (Project project : projectList) {
        ++offset;
        try {
          projectBusinessService.transitionBetweenPaidStatus(project);
          incrementDone();
        } catch (Exception e) {
          incrementAnomaly();
          TraceBackService.trace(
              e,
              String.format(
                  I18n.get(
                      BusinessProjectExceptionMessage.BATCH_TRANSITION_BETWEEN_PAID_STATUS_ERROR),
                  project.getId()),
              batch.getId());
        }
      }
      JPA.clear();
    }
  }

  @Override
  protected void stop() {

    String comment =
        String.format(
            "\t "
                + I18n.get(BusinessProjectExceptionMessage.BATCH_TRANSITION_BETWEEN_PAID_STATUS)
                + "\n",
            batch.getDone());

    comment +=
        String.format("\t" + I18n.get(BaseExceptionMessage.BASE_BATCH_3), batch.getAnomaly());

    addComment(comment);
    super.stop();
  }

  protected void setBatchTypeSelect() {
    this.batch.setBatchTypeSelect(BatchRepository.BATCH_TYPE_BUSINESS_PROJECT_BATCH);
  }
}
