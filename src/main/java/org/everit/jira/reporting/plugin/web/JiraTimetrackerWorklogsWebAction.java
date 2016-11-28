/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.jira.reporting.plugin.web;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.everit.jira.analytics.AnalyticsDTO;
import org.everit.jira.core.SupportManager;
import org.everit.jira.core.util.TimetrackerUtil;
import org.everit.jira.reporting.plugin.ReportingCondition;
import org.everit.jira.reporting.plugin.dto.MissingsPageingDTO;
import org.everit.jira.reporting.plugin.dto.MissingsWorklogsDTO;
import org.everit.jira.settings.TimetrackerSettingsHelper;
import org.everit.jira.settings.dto.TimeTrackerGlobalSettings;
import org.everit.jira.timetracker.plugin.JiraTimetrackerAnalytics;
import org.everit.jira.timetracker.plugin.PluginCondition;
import org.everit.jira.timetracker.plugin.util.ExceptionUtil;
import org.everit.jira.timetracker.plugin.util.PiwikPropertiesUtil;
import org.everit.jira.timetracker.plugin.util.PropertiesUtil;
import org.everit.jira.updatenotifier.UpdateNotifier;
import org.joda.time.DateTime;
import org.ofbiz.core.entity.GenericEntityException;

import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.web.action.JiraWebActionSupport;

/**
 * Missing worklogs page.
 */
public class JiraTimetrackerWorklogsWebAction extends JiraWebActionSupport {

  private static final String JIRA_HOME_URL = "/secure/Dashboard.jspa";

  /**
   * Logger.
   */
  private static final Logger LOGGER = Logger
      .getLogger(JiraTimetrackerWorklogsWebAction.class);

  private static final String PARAM_ACTUAL_PAGE = "actualPage";

  private static final String PARAM_DATE_FROM_FORMATED = "dateFromFormated";

  private static final String PARAM_DATE_TO_FORMATED = "dateToFormated";

  private static final String PARAM_DATEFROM = "dateFromMil";

  private static final String PARAM_DATETO = "dateToMil";

  /**
   * The number of rows in the dates table.
   */
  private static final int ROW_COUNT = 20;

  /**
   * Serial version UID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The actual page.
   */
  private int actualPage;

  private List<MissingsWorklogsDTO> allDatesWhereNoWorklog;

  private AnalyticsDTO analyticsDTO;

  /**
   * The report check the worklogs time spent is equal or greater than 8 hours.
   */
  public boolean checkHours = false;

  /**
   * If check the worklogs spent time, then exclude the non working issues, or not.
   */
  public boolean checkNonWorkingIssues = false;

  private String contextPath;

  private DateTime dateFrom;

  /**
   * The formated date.
   */
  private Long dateFromFormated;

  private DateTime dateTo;

  /**
   * The formated date.
   */
  private Long dateToFormated;

  private String issueCollectorSrc;

  /**
   * The message.
   */
  private String message = "";

  /**
   * The message parameter.
   */
  private String messageParameter = "";

  /**
   * The number of pages.
   */
  private int numberOfPages;

  private MissingsPageingDTO paging = new MissingsPageingDTO();

  private PluginCondition pluginCondition;

  private ReportingCondition reportingCondition;

  private TimetrackerSettingsHelper settingsHelper;

  private List<MissingsWorklogsDTO> showDatesWhereNoWorklog;

  private String stacktrace = "";

  private SupportManager supportManager;

  /**
   * Simple constructor.
   */
  public JiraTimetrackerWorklogsWebAction(
      final SupportManager supportManager,
      final TimetrackerSettingsHelper settingsHelper) {
    this.supportManager = supportManager;
    this.settingsHelper = settingsHelper;
    reportingCondition = new ReportingCondition(settingsHelper);
    pluginCondition = new PluginCondition(settingsHelper);
  }

  private String checkConditions() {
    boolean isUserLogged = TimetrackerUtil.isUserLogged();
    if (!isUserLogged) {
      setReturnUrl(JIRA_HOME_URL);
      return getRedirect(NONE);
    }
    if (!reportingCondition.shouldDisplay(getLoggedInApplicationUser(), null)) {
      setReturnUrl(JIRA_HOME_URL);
      return getRedirect(NONE);
    }
    if (!pluginCondition.shouldDisplay(getLoggedInApplicationUser(), null)) {
      setReturnUrl(JIRA_HOME_URL);
      return getRedirect(NONE);
    }
    return null;
  }

  /**
   * Count how much page need to show the dates.
   *
   * @return Number of pages.
   */
  private int countNumberOfPages() {
    int numberOfPages = 0;
    numberOfPages = allDatesWhereNoWorklog.size() / ROW_COUNT;
    if ((allDatesWhereNoWorklog.size() % ROW_COUNT) != 0) {
      numberOfPages++;
    }
    return numberOfPages;
  }

  /**
   * Set dateFrom and dateFromFormated default value.
   */
  private void dateFromDefaultInit() {
    DateTime date = new DateTime(TimetrackerUtil.getLoggedUserTimeZone());
    date = date.minusMonths(1);
    dateFromFormated = date.getMillis();
  }

  /**
   * Set dateTo and dateToFormated default values.
   */
  private void dateToDefaultInit() {
    DateTime date = new DateTime(TimetrackerUtil.getLoggedUserTimeZone());
    dateToFormated = date.getMillis();
  }

  @Override
  public String doDefault() throws ParseException {
    String checkConditionsResult = checkConditions();
    if (checkConditionsResult != null) {
      return checkConditionsResult;
    }

    normalizeContextPath();
    loadIssueCollectorSrc();
    TimeTrackerGlobalSettings globalSettings = settingsHelper.loadGlobalSettings();
    analyticsDTO = JiraTimetrackerAnalytics
        .getAnalyticsDTO(PiwikPropertiesUtil.PIWIK_WORKLOGS_SITEID, settingsHelper);

    if (dateToFormated == null) {
      dateToDefaultInit();
    }
    dateTo = new DateTime(dateToFormated);
    dateTo = dateTo.withZoneRetainFields(TimetrackerUtil.getLoggedUserTimeZone());

    if (dateFromFormated == null) {
      dateFromDefaultInit();
    }
    dateFrom = new DateTime(dateFromFormated);
    dateFrom = dateFrom.withZoneRetainFields(TimetrackerUtil.getLoggedUserTimeZone());

    try {
      // TODO not simple "" for selectedUser. Use user picker
      // Default check box parameter false, false
      allDatesWhereNoWorklog = supportManager
          .getDates(dateFrom, dateTo, checkHours,
              checkNonWorkingIssues, globalSettings);
    } catch (GenericEntityException e) {
      LOGGER.error("Error when try to run the query.", e);
      stacktrace = ExceptionUtil.getStacktrace(e);
      return ERROR;
    }
    numberOfPages = countNumberOfPages();
    actualPage = 1;
    setShowDatesListByActualPage(actualPage);
    return INPUT;
  }

  @Override
  public String doExecute() throws ParseException {
    String checkConditionsResult = checkConditions();
    if (checkConditionsResult != null) {
      return checkConditionsResult;
    }
    TimeTrackerGlobalSettings globalSettings = settingsHelper.loadGlobalSettings();

    normalizeContextPath();
    loadIssueCollectorSrc();

    analyticsDTO = JiraTimetrackerAnalytics
        .getAnalyticsDTO(PiwikPropertiesUtil.PIWIK_WORKLOGS_SITEID, settingsHelper);

    initVariables();
    String searchActionResult = parseParams();
    if (searchActionResult != null) {
      return searchActionResult;
    }
    try {
      // TODO not simple "" for selectedUser. Use user picker
      allDatesWhereNoWorklog = supportManager
          .getDates(dateFrom, dateTo, checkHours,
              checkNonWorkingIssues, globalSettings);
    } catch (GenericEntityException e) {
      LOGGER.error("Error when try to run the query.", e);
      stacktrace = ExceptionUtil.getStacktrace(e);
      return ERROR;
    }
    // check the page changer buttons
    numberOfPages = countNumberOfPages();
    pageChangeAction();
    setShowDatesListByActualPage(actualPage);

    return SUCCESS;
  }

  public int getActualPage() {
    return actualPage;
  }

  public AnalyticsDTO getAnalyticsDTO() {
    return analyticsDTO;
  }

  public boolean getCheckHours() {
    return checkHours;
  }

  public boolean getCheckNonWorkingIssues() {
    return checkNonWorkingIssues;
  }

  public String getContextPath() {
    return contextPath;
  }

  public Long getDateFromFormated() {
    return dateFromFormated;
  }

  public List<MissingsWorklogsDTO> getDateswhereNoWorklog() {
    return allDatesWhereNoWorklog;
  }

  @Override
  public DateTimeFormatter getDateTimeFormatter() {
    return super.getDateTimeFormatter().withStyle(DateTimeStyle.DATE).withSystemZone();
  }

  public Long getDateToFormated() {
    return dateToFormated;
  }

  public String getIssueCollectorSrc() {
    return issueCollectorSrc;
  }

  public String getMessage() {
    return message;
  }

  public String getMessageParameter() {
    return messageParameter;
  }

  public int getNumberOfPages() {
    return numberOfPages;
  }

  public MissingsPageingDTO getPaging() {
    return paging;
  }

  public List<MissingsWorklogsDTO> getShowDatesWhereNoWorklog() {
    return showDatesWhereNoWorklog;
  }

  public String getStacktrace() {
    return stacktrace;
  }

  private void initVariables() {
    message = "";
    messageParameter = "";
    allDatesWhereNoWorklog = new ArrayList<>();
    showDatesWhereNoWorklog = new ArrayList<>();
  }

  private void loadIssueCollectorSrc() {
    Properties properties = PropertiesUtil.getJttpBuildProperties();
    issueCollectorSrc = properties.getProperty(PropertiesUtil.ISSUE_COLLECTOR_SRC);
  }

  private void normalizeContextPath() {
    String path = getHttpRequest().getContextPath();
    if ((path.length() > 0) && "/".equals(path.substring(path.length() - 1))) {
      contextPath = path.substring(0, path.length() - 1);
    } else {
      contextPath = path;
    }
  }

  /**
   * Handle the page changer action.
   */
  public void pageChangeAction() {
    String dayBackValue = getHttpRequest().getParameter("pageBack");
    String dayNextValue = getHttpRequest().getParameter("pageNext");
    String paging = getHttpRequest().getParameter("paging");
    if ((dayBackValue != null) && (actualPage > 1)) {
      actualPage--;
    }
    if ((dayNextValue != null) && (actualPage < numberOfPages)) {
      actualPage++;
    }
    if (paging != null) {
      actualPage = Integer.parseInt(paging);
    }
  }

  private void parseCheckboxParam() {
    String hourValue = getHttpRequest().getParameter("hour");
    String nonworkingValue = getHttpRequest().getParameter("nonworking");
    if (hourValue != null) {
      checkHours = true;
    }
    if (nonworkingValue != null) {
      checkNonWorkingIssues = true;
    }
  }

  private void parseDateParams() {
    String requestDateFrom = getHttpRequest().getParameter(PARAM_DATEFROM);
    if (requestDateFrom != null) {
      dateFromFormated = Long.valueOf(requestDateFrom);
    } else if (dateFromFormated == null) {
      dateFromDefaultInit();
    }
    dateFrom = new DateTime(dateFromFormated);
    dateFrom = dateFrom.withZoneRetainFields(TimetrackerUtil.getLoggedUserTimeZone());

    String requestDateTo = getHttpRequest().getParameter(PARAM_DATETO);
    if (requestDateTo != null) {
      dateToFormated = Long.valueOf(requestDateTo);
    } else if (dateToFormated == null) {
      dateToDefaultInit();
    }
    dateTo = new DateTime(dateToFormated);
    dateTo = dateTo.withZoneRetainFields(TimetrackerUtil.getLoggedUserTimeZone());

  }

  private void parsePagingParams() {
    String requestDateFromFormated = getHttpRequest().getParameter(PARAM_DATE_FROM_FORMATED);
    if (requestDateFromFormated != null) {
      dateFromFormated = Long.valueOf(requestDateFromFormated);
    }
    String requestDateToFormated = getHttpRequest().getParameter(PARAM_DATE_TO_FORMATED);
    if (requestDateToFormated != null) {
      dateToFormated = Long.valueOf(requestDateToFormated);
    }
    actualPage = Integer.parseInt(getHttpRequest().getParameter(PARAM_ACTUAL_PAGE));
  }

  private String parseParams() throws ParseException {
    String searchValue = getHttpRequest().getParameter("search");
    // set actual page default! we start the new query with the first page
    actualPage = 1;
    if (searchValue != null) {
      parseDateParams();
      if (dateFrom.compareTo(dateTo) >= 0) {
        message = "plugin.wrong.dates";
        return INPUT;
      }
    } else {
      parsePagingParams();
      dateFrom = new DateTime(dateFromFormated);
      dateTo = new DateTime(dateToFormated);
    }
    parseCheckboxParam();
    return null;
  }

  private void readObject(final java.io.ObjectInputStream stream) throws IOException,
      ClassNotFoundException {
    stream.close();
    throw new java.io.NotSerializableException(getClass().getName());
  }

  /**
   * Decide render or not the update information bar.
   *
   * @return true if bar should be render
   */
  public boolean renderUpdateNotifier() {
    return new UpdateNotifier(settingsHelper).isShowUpdater();
  }

  public void setActualPage(final int actualPage) {
    this.actualPage = actualPage;
  }

  public void setCheckHours(final boolean checkHours) {
    this.checkHours = checkHours;
  }

  public void setCheckNonWorkingIssues(final boolean checkNonWorkingIssues) {
    this.checkNonWorkingIssues = checkNonWorkingIssues;
  }

  public void setContextPath(final String contextPath) {
    this.contextPath = contextPath;
  }

  public void setDateFromFormated(final Long dateFromFormated) {
    this.dateFromFormated = dateFromFormated;
  }

  public void setDateswhereNoWorklog(final List<MissingsWorklogsDTO> dateswhereNoWorklog) {
    allDatesWhereNoWorklog = dateswhereNoWorklog;
  }

  public void setDateToFormated(final Long dateToFormated) {
    this.dateToFormated = dateToFormated;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public void setMessageParameter(final String messageParameter) {
    this.messageParameter = messageParameter;
  }

  public void setNumberOfPages(final int numberOfPages) {
    this.numberOfPages = numberOfPages;
  }

  public void setPaging(final MissingsPageingDTO paging) {
    this.paging = paging;
  }

  /**
   * Set the showDatesWhereNoWorklog by the actual page.
   *
   * @param actualPageParam
   *          The sub list of allDatesWhereNoWorklog.
   */
  private void setShowDatesListByActualPage(final int actualPageParam) {
    if (allDatesWhereNoWorklog.size() > 0) { // if the result is 0, use dafault paging
      int from = (actualPageParam - 1) * ROW_COUNT;
      int to = actualPageParam * ROW_COUNT;
      if ((actualPageParam == 1) && (allDatesWhereNoWorklog.size() < ROW_COUNT)) {
        to = allDatesWhereNoWorklog.size();
      }
      if ((actualPageParam == numberOfPages)
          && ((allDatesWhereNoWorklog.size() % ROW_COUNT) != 0)) {
        to = from + (allDatesWhereNoWorklog.size() % ROW_COUNT);
      }
      paging = paging.start(from + 1).end(to).resultSize(allDatesWhereNoWorklog.size())
          .actPageNumber(actualPageParam).maxPageNumber(numberOfPages);
      showDatesWhereNoWorklog = allDatesWhereNoWorklog.subList(from, to);
    }
  }

  public void setShowDatesWhereNoWorklog(
      final List<MissingsWorklogsDTO> showDatesWhereNoWorklog) {
    this.showDatesWhereNoWorklog = showDatesWhereNoWorklog;
  }

  private void writeObject(final java.io.ObjectOutputStream stream) throws IOException {
    stream.close();
    throw new java.io.NotSerializableException(getClass().getName());
  }

}
