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
package com.axelor.apps.mobilesettings.rest;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.mobilesettings.db.MobileDashboard;
import com.axelor.apps.mobilesettings.rest.dto.MobileDashboardResponse;
import com.axelor.apps.mobilesettings.service.MobileDashboardResponseComputeService;
import com.axelor.apps.mobilesettings.translation.MobileSettingsTranslation;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.utils.api.HttpExceptionHandler;
import com.axelor.utils.api.ObjectFinder;
import com.axelor.utils.api.ResponseConstructor;
import com.axelor.utils.api.SecurityCheck;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Optional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import wslite.json.JSONException;

@Path("/aos/mobiledashboard")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MobileDashboardRestController {
  @Operation(
      summary = "Get mobile dashboard",
      tags = {"Mobile Dashboard"})
  @Path("/{mobileDashboardId}")
  @GET
  @HttpExceptionHandler
  public Response getMobileDashboard(@PathParam("mobileDashboardId") Long mobileDashboardId)
      throws AxelorException, JSONException {
    new SecurityCheck().readAccess(MobileDashboard.class, mobileDashboardId).check();
    MobileDashboard mobileDashboard =
        ObjectFinder.find(MobileDashboard.class, mobileDashboardId, ObjectFinder.NO_VERSION);

    Optional<MobileDashboardResponse> response =
        Beans.get(MobileDashboardResponseComputeService.class)
            .computeMobileDashboardResponse(mobileDashboard);

    if (response.isEmpty()) {
      return ResponseConstructor.build(
          Response.Status.FORBIDDEN, I18n.get(MobileSettingsTranslation.NO_ACCESS_TO_RECORD));
    }

    return ResponseConstructor.build(
        Response.Status.OK, I18n.get(MobileSettingsTranslation.QUERY_RESPONSE_CHART), response);
  }
}
