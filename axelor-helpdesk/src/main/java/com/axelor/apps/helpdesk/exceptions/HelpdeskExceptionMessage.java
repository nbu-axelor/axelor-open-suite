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
package com.axelor.apps.helpdesk.exceptions;

public final class HelpdeskExceptionMessage {

  private HelpdeskExceptionMessage() {}

  public static final String SELECT_TICKETS = /*$$(*/ "Please select tickets" /*)*/;
  public static final String UPDATE_TICKET_WORKFLOW = /*$$(*/
      "Workflow to update status to value provided is not supported by ticket." /*)*/;

  public static final String ON_GOING_TICKET_STATUS_DONT_EXIST = /*$$(*/
      "On going ticket status does not exist. Please configure at least one." /*)*/;

  public static final String RESOLVED_TICKET_STATUS_DONT_EXIST = /*$$(*/
      "Resolved ticket status does not exist. Please configure at least one." /*)*/;

  public static final String CLOSED_TICKET_STATUS_DONT_EXIST = /*$$(*/
      "Closed ticket status does not exist. Please configure at least one." /*)*/;

  public static final String DEFAULT_TICKET_STATUS_DONT_EXIST = /*$$(*/
      "Default ticket status does not exist. Please configure at least one." /*)*/;
}
