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
package org.everit.jira.core.impl;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.everit.jira.core.EVWorklogManager;
import org.everit.jira.core.impl.WorklogComponent.PropertiesKey;
import org.everit.jira.timetracker.plugin.dto.ActionResult;
import org.everit.jira.timetracker.plugin.dto.ActionResultStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mockito;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.issue.worklog.WorklogInputParametersImpl;
import com.atlassian.jira.bc.issue.worklog.WorklogNewEstimateInputParameters;
import com.atlassian.jira.bc.issue.worklog.WorklogResult;
import com.atlassian.jira.bc.issue.worklog.WorklogService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogImpl;
import com.atlassian.jira.mock.component.MockComponentWorker;
import com.atlassian.jira.mock.issue.MockIssue;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.MockApplicationUser;
import com.atlassian.jira.util.I18nHelper;

public class CreateWorklogTest {

  static class DummySuccessWorklogResult implements WorklogResult {

    private final Issue issue;

    public DummySuccessWorklogResult(final Issue issue) {
      this.issue = issue;
    }

    @Override
    public Worklog getWorklog() {
      return new WorklogImpl(null, issue, 1L, "", "", new Date(), "", 1L, 1L);
    }

    @Override
    public boolean isEditableCheckRequired() {
      return true;
    }

  }

  private MockIssue createErrorIssue;

  private MockIssue dateParseErrorIssue;

  private String invalidIssueId;

  private MockIssue noPermissionIssue;

  private MockIssue succesCreateIssue;

  private MockIssue validateProblemIssue;

  private EVWorklogManager worklogManager;

  private void assertActionResult(final ActionResult result,
      final ActionResultStatus expectedStatus, final String expectedIssueId,
      final String expectedMessage) {
    Assert.assertNotNull(result);
    Assert.assertEquals(expectedStatus, result.getStatus());
    Assert.assertEquals(expectedIssueId, result.getMessageParameter());
    Assert.assertEquals(expectedMessage, result.getMessage());
  }

  @Before
  public void before() throws ParseException {
    MockComponentWorker mockComponentWorker = new MockComponentWorker();

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    c.set(Calendar.HOUR, 8);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    Date defaultStartDate = c.getTime();

    // mocked components
    JiraAuthenticationContext mockJiraAuthenticationContext =
        Mockito.mock(JiraAuthenticationContext.class, Mockito.RETURNS_DEEP_STUBS);
    I18nHelper i18nHelper = Mockito.mock(I18nHelper.class, Mockito.RETURNS_DEEP_STUBS);
    IssueManager issueManager = Mockito.mock(IssueManager.class, Mockito.RETURNS_DEEP_STUBS);
    PermissionManager permissionManager =
        Mockito.mock(PermissionManager.class, Mockito.RETURNS_DEEP_STUBS);
    WorklogService worklogService = Mockito.mock(WorklogService.class, Mockito.RETURNS_DEEP_STUBS);

    // logged user
    MockApplicationUser loggedUser = new MockApplicationUser("test_userkey", "test_username");
    Mockito.when(mockJiraAuthenticationContext.getUser())
        .thenReturn(loggedUser);
    Mockito.when(mockJiraAuthenticationContext.getI18nHelper())
        .thenReturn(i18nHelper);
    Mockito.when(i18nHelper.getLocale())
        .thenReturn(Locale.ENGLISH);

    // incalid issue
    invalidIssueId = "invalidId";
    Mockito.when(issueManager.getIssueObject(invalidIssueId))
        .thenReturn(null);

    // no permission in issue
    noPermissionIssue = new MockIssue(1, "KEY-1");
    Mockito.when(issueManager.getIssueObject(noPermissionIssue.getKey()))
        .thenReturn(noPermissionIssue);
    Mockito.when(
        permissionManager.hasPermission(Permissions.WORK_ISSUE,
            noPermissionIssue,
            loggedUser))
        .thenReturn(false);
    Mockito.when(
        worklogService.hasPermissionToCreate((JiraServiceContext) Matchers.any(),
            Matchers.eq(noPermissionIssue),
            Matchers.eq(true)))
        .thenReturn(false);

    // date parse error issue
    dateParseErrorIssue = new MockIssue(2, "KEY-2");
    Mockito.when(issueManager.getIssueObject(dateParseErrorIssue.getKey()))
        .thenReturn(dateParseErrorIssue);
    Mockito.when(
        permissionManager.hasPermission(Permissions.WORK_ISSUE,
            dateParseErrorIssue,
            loggedUser))
        .thenReturn(true);

    // validate error issue
    validateProblemIssue = new MockIssue(3, "KEY-3");
    Mockito.when(issueManager.getIssueObject(validateProblemIssue.getKey()))
        .thenReturn(validateProblemIssue);
    Mockito.when(
        permissionManager.hasPermission(Permissions.WORK_ISSUE,
            validateProblemIssue,
            loggedUser))
        .thenReturn(true);
    Mockito.when(
        worklogService.hasPermissionToCreate(Matchers.any(JiraServiceContext.class),
            Matchers.eq(validateProblemIssue),
            Matchers.eq(true)))
        .thenReturn(true);
    Mockito.when(
        worklogService.validateCreate(Matchers.any(JiraServiceContext.class),
            Matchers.argThat(new ArgumentMatcher<WorklogNewEstimateInputParameters>() {
              @Override
              public boolean matches(final Object argument) {
                if (argument == null) {
                  return false;
                }
                return validateProblemIssue.getKey()
                    .equals(((WorklogNewEstimateInputParameters) argument).getIssue().getKey());
              }
            })))
        .thenReturn(null);

    // create error issue
    createErrorIssue = new MockIssue(4, "KEY-4");
    Mockito.when(issueManager.getIssueObject(createErrorIssue.getKey()))
        .thenReturn(createErrorIssue);
    Mockito.when(
        permissionManager.hasPermission(Permissions.WORK_ISSUE,
            createErrorIssue,
            loggedUser))
        .thenReturn(true);
    Mockito.when(
        worklogService.hasPermissionToCreate(Matchers.any(JiraServiceContext.class),
            Matchers.eq(createErrorIssue),
            Matchers.eq(true)))
        .thenReturn(true);
    DummySuccessWorklogResult dummySuccessWorklogResult =
        new DummySuccessWorklogResult(createErrorIssue);
    Mockito.when(
        worklogService.validateCreate(Matchers.any(JiraServiceContext.class),
            Matchers.argThat(new ArgumentMatcher<WorklogNewEstimateInputParameters>() {
              @Override
              public boolean matches(final Object argument) {
                if (argument == null) {
                  return false;
                }
                return createErrorIssue.getKey()
                    .equals(((WorklogNewEstimateInputParameters) argument).getIssue().getKey());
              }
            })))
        .thenReturn(dummySuccessWorklogResult);
    Mockito.when(
        worklogService.createAndAutoAdjustRemainingEstimate(Matchers.any(JiraServiceContext.class),
            Matchers.eq(dummySuccessWorklogResult),
            Matchers.eq(true)))
        .thenReturn(null);

    // success create
    succesCreateIssue = new MockIssue(5, "KEY-5");
    Mockito.when(issueManager.getIssueObject(succesCreateIssue.getKey()))
        .thenReturn(succesCreateIssue);
    Mockito.when(
        permissionManager.hasPermission(Permissions.WORK_ISSUE,
            succesCreateIssue,
            loggedUser))
        .thenReturn(true);
    Mockito.when(
        worklogService.hasPermissionToCreate(Matchers.any(JiraServiceContext.class),
            Matchers.eq(succesCreateIssue),
            Matchers.eq(true)))
        .thenReturn(true);
    WorklogNewEstimateInputParameters succesCreateIssueParams = WorklogInputParametersImpl
        .issue(succesCreateIssue)
        .startDate(defaultStartDate)
        .timeSpent("2h")
        .comment("comment")
        .buildNewEstimate();
    DummySuccessWorklogResult dummySuccessCreateWorklogResult =
        new DummySuccessWorklogResult(succesCreateIssue);
    Mockito.when(
        worklogService.validateCreate(Matchers.any(JiraServiceContext.class),
            Matchers.eq(succesCreateIssueParams)))
        .thenReturn(dummySuccessCreateWorklogResult);
    Mockito.when(
        worklogService.createAndAutoAdjustRemainingEstimate(Matchers.any(JiraServiceContext.class),
            Matchers.eq(dummySuccessCreateWorklogResult),
            Matchers.eq(true)))
        .thenReturn(Mockito.mock(Worklog.class));

    // init components
    mockComponentWorker.addMock(JiraAuthenticationContext.class, mockJiraAuthenticationContext)
        .addMock(IssueManager.class, issueManager)
        .addMock(PermissionManager.class, permissionManager)
        .addMock(WorklogService.class, worklogService)
        .init();

    worklogManager = new WorklogComponent();
  }

  @Test
  public void testCreateWorklog() {
    String defaultComment = "comment";
    Date defaultDate = new Date();
    String defaultStartTime = "08:00";
    String defaultTimeSpent = "1h";

    ActionResult result = worklogManager.createWorklog(invalidIssueId,
        defaultComment,
        defaultDate,
        defaultStartTime,
        defaultTimeSpent);
    assertActionResult(result, ActionResultStatus.FAIL,
        invalidIssueId,
        PropertiesKey.INVALID_ISSUE);

    result = worklogManager.createWorklog(noPermissionIssue.getKey(),
        defaultComment,
        defaultDate,
        defaultStartTime,
        defaultTimeSpent);
    assertActionResult(result, ActionResultStatus.FAIL,
        noPermissionIssue.getKey(),
        PropertiesKey.NOPERMISSION_ISSUE);

    String wrongTimeStamp = "wrong";
    result = worklogManager.createWorklog(dateParseErrorIssue.getKey(),
        defaultComment,
        defaultDate,
        wrongTimeStamp,
        defaultTimeSpent);
    assertActionResult(result, ActionResultStatus.FAIL,
        defaultDate + " " + wrongTimeStamp,
        PropertiesKey.DATE_PARSE);

    result = worklogManager.createWorklog(validateProblemIssue.getKey(),
        defaultComment,
        defaultDate,
        defaultStartTime,
        defaultTimeSpent);
    assertActionResult(result, ActionResultStatus.FAIL,
        "",
        PropertiesKey.WORKLOG_CREATE_FAIL);

    result = worklogManager.createWorklog(createErrorIssue.getKey(),
        defaultComment,
        defaultDate,
        defaultStartTime,
        defaultTimeSpent);
    assertActionResult(result, ActionResultStatus.FAIL,
        "",
        PropertiesKey.WORKLOG_CREATE_FAIL);

    result = worklogManager.createWorklog(succesCreateIssue.getKey(),
        defaultComment,
        defaultDate,
        defaultStartTime,
        defaultTimeSpent);
    assertActionResult(result, ActionResultStatus.SUCCESS,
        "",
        PropertiesKey.WORKLOG_CREATE_SUCCESS);
  }
}
