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
package org.everit.jira.reporting.plugin.query;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.everit.jira.querydsl.schema.QProject;
import org.everit.jira.reporting.plugin.dto.ProjectSummaryDTO;
import org.everit.jira.reporting.plugin.dto.ReportSearchParam;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.SQLExpressions;
import com.querydsl.sql.SQLQuery;

/**
 * Query for project summary report.
 */
public class ProjectSummaryReportQuery extends AbstractListReportQuery<ProjectSummaryDTO> {

  public ProjectSummaryReportQuery(final ReportSearchParam reportSearchParam) {
    super(reportSearchParam);
  }

  @Override
  public List<ProjectSummaryDTO> call(final Connection connection,
      final Configuration configuration)
          throws SQLException {
    SimplePath<Long> fromProjectIdPath =
        Expressions.path(Long.class, new PathMetadata(null, "fromProjectId", PathType.VARIABLE));
    SimplePath<Long> timeOriginalSumPath = Expressions.path(Long.class, new PathMetadata(null,
        ProjectSummaryDTO.AliasNames.ISSUE_TIME_ORIGINAL_ESTIMATE_SUM, PathType.VARIABLE));
    SimplePath<Long> timeEstimateSumPath = Expressions.path(Long.class, new PathMetadata(null,
        ProjectSummaryDTO.AliasNames.ISSUE_TIME_ESTIMATE_SUM, PathType.VARIABLE));
    SimplePath<Long> workloggedSumPath = Expressions.path(Long.class, new PathMetadata(null,
        ProjectSummaryDTO.AliasNames.WORKLOGGED_TIME_SUM, PathType.VARIABLE));

    SQLQuery<Tuple> fromQuery = SQLExpressions.select(
        qProject.id.as(fromProjectIdPath),
        qIssue.timeoriginalestimate.sum().as(timeOriginalSumPath),
        qIssue.timeestimate.sum().as(timeEstimateSumPath),
        qWorklog.timeworked.sum().as(workloggedSumPath));

    appendBaseFromAndJoin(fromQuery);
    appendBaseWhere(fromQuery);
    fromQuery.groupBy(qProject.id);

    QProject qProject = new QProject("m_project");
    return new SQLQuery<ProjectSummaryDTO>(connection, configuration)
        .select(Projections.bean(ProjectSummaryDTO.class,
            qProject.pkey.as(ProjectSummaryDTO.AliasNames.PROJECT_KEY),
            qProject.pname.as(ProjectSummaryDTO.AliasNames.PROJECT_NAME),
            qProject.description.as(ProjectSummaryDTO.AliasNames.PROJECT_DESCRIPTION),
            timeOriginalSumPath,
            timeEstimateSumPath,
            workloggedSumPath))
        .from(fromQuery.as("sums"))
        .join(qProject).on(qProject.id.eq(fromProjectIdPath))
        .fetch();
  }

}
