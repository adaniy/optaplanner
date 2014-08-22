/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.benchmark.impl.statistic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamInclude;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.apache.commons.io.IOUtils;
import org.optaplanner.benchmark.impl.report.ReportHelper;
import org.optaplanner.benchmark.impl.result.SingleBenchmarkResult;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.impl.score.definition.ScoreDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1 statistic of {@link SingleBenchmarkResult}
 */
@XStreamInclude({
        PureSingleStatistic.class
})
public abstract class SingleStatistic<P extends StatisticPoint> {

    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    @XStreamOmitField // Bi-directional relationship restored through BenchmarkResultIO
    protected SingleBenchmarkResult singleBenchmarkResult;

    @XStreamOmitField
    protected List<P> pointList;

    protected SingleStatistic(SingleBenchmarkResult singleBenchmarkResult) {
        this.singleBenchmarkResult = singleBenchmarkResult;
        initPointList();
    }

    public SingleBenchmarkResult getSingleBenchmarkResult() {
        return singleBenchmarkResult;
    }

    public void setSingleBenchmarkResult(SingleBenchmarkResult singleBenchmarkResult) {
        this.singleBenchmarkResult = singleBenchmarkResult;
    }

    public abstract StatisticType getStatisticType();

    public List<P> getPointList() {
        return pointList;
    }
    public void setPointList(List<P> pointList) {
        this.pointList = pointList;
    }

    public String getCsvFilePath() {
        return singleBenchmarkResult.getSingleReportDirectoryPath() + "/" + getStatisticType().name() + ".csv";
    }

    public File getCsvFile() {
        return new File(singleBenchmarkResult.getBenchmarkReportDirectory(), getCsvFilePath());
    }

    // ************************************************************************
    // Lifecycle methods
    // ************************************************************************

    public abstract void open(Solver solver);

    public abstract void close(Solver solver);

    // ************************************************************************
    // Write methods
    // ************************************************************************

    public void initPointList() {
        pointList = new ArrayList<P>();
    }

    protected abstract String getCsvHeader();

    public void writeCsvStatisticFile() {
        File csvFile = getCsvFile();
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8");
            writer.append(getCsvHeader()).append("\n");
            for (StatisticPoint point : getPointList()) {
                writer.append(point.toCsvLine()).append("\n");
            }
            if (singleBenchmarkResult.isFailure()) {
                writer.append("Failed\n");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed writing csvFile (" + csvFile + ").", e);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    public void readCsvStatisticFile() {
        File csvFile = getCsvFile();
        ScoreDefinition scoreDefinition = singleBenchmarkResult.getSolverBenchmarkResult().getSolverConfig()
                .getScoreDirectorFactoryConfig().buildScoreDefinition();
        if (!pointList.isEmpty()) {
            throw new IllegalStateException("The pointList with size (" + pointList.size() + ") should be empty.");
        }
        if (!csvFile.exists()) {
            if (singleBenchmarkResult.isFailure()) {
                pointList = Collections.emptyList();
                return;
            } else {
                throw new IllegalStateException("The csvFile (" + csvFile + ") does not exist.");
            }
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "UTF-8"));
            String line = reader.readLine();
            if (!getCsvHeader().equals(line)) {
                throw new IllegalStateException("The read line (" + line
                        + ") is expected to be the header line (" + getCsvHeader()
                        + ") for statisticType (" + getStatisticType() + ").");
            }
            for (line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                List<String> csvLine = StatisticPoint.parseCsvLine(line);
                pointList.add(createPointFromCsvLine(scoreDefinition, csvLine));
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed reading csvFile (" + csvFile + ").", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed reading csvFile (" + csvFile + ").", e);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    protected abstract P createPointFromCsvLine(ScoreDefinition scoreDefinition, List<String> csvLine);

    // ************************************************************************
    // Report accumulates
    // ************************************************************************

    public String getAnchorId() {
        return ReportHelper.escapeHtmlId(singleBenchmarkResult.getName() + "_" + getStatisticType().name());
    }

}
