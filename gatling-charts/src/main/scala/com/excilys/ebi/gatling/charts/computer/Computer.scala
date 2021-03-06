/**
 * Copyright 2011-2012 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.charts.computer

import scala.collection.SortedMap
import scala.math.{sqrt, pow}

import com.excilys.ebi.gatling.core.action.EndAction.END_OF_SCENARIO
import com.excilys.ebi.gatling.core.action.StartAction.START_OF_SCENARIO
import com.excilys.ebi.gatling.core.log.Logging
import com.excilys.ebi.gatling.core.result.message.ResultStatus.{ResultStatus, OK, KO}
import com.excilys.ebi.gatling.core.result.writer.ResultLine

object Computer extends Logging {
	
	val AVERAGE_TIME_NO_PLOT_MAGIC_VALUE = -1
	

	def averageTime(timeFunction: ResultLine => Long, data: Seq[ResultLine]): Int = {
		if (data.isEmpty)
			AVERAGE_TIME_NO_PLOT_MAGIC_VALUE
		else
			(data.map(timeFunction(_)).sum / data.length.toDouble).toInt
	}
	
	def averageResponseTime = averageTime((line: ResultLine) => line.responseTime, _: Seq[ResultLine])
	
	def averageLatency = averageTime((line: ResultLine) => line.latency, _: Seq[ResultLine])

	def responseTimeStandardDeviation(data: Seq[ResultLine]): Double = {
		val avg = averageResponseTime(data)
		sqrt(data.map(result => pow(result.responseTime - avg, 2)).sum / data.length)
	}

	def minResponseTime(data: Seq[ResultLine]): Long = data.minBy(_.responseTime).responseTime

	def maxResponseTime(data: Seq[ResultLine]): Long = data.maxBy(_.responseTime).responseTime

	def numberOfSuccesses(data: Seq[ResultLine]): Int = data.filter(_.resultStatus == OK).size

	def responseTimeByMillisecondAsList(data: SortedMap[Long, Seq[ResultLine]], resultStatus: ResultStatus): List[(Long, Int)] =
		SortedMap(data.map(entry => entry._1 -> entry._2.filter(_.resultStatus == resultStatus)).map { entry =>
			val (date, list) = entry
			entry._1 -> averageResponseTime(list)
		}.toSeq: _*).toList
		
	def latencyByMillisecondAsList(data: SortedMap[Long, Seq[ResultLine]], resultStatus: ResultStatus): List[(Long, Int)] =
		SortedMap(data.map(entry => entry._1 -> entry._2.filter(_.resultStatus == resultStatus)).map { entry =>
			val (date, list) = entry
			entry._1 -> averageLatency(list)
		}.toSeq: _*).toList

	def numberOfRequestsPerSecond(data: SortedMap[Long, Seq[ResultLine]]): SortedMap[Long, Int] = SortedMap(data.map(entry => entry._1 -> entry._2.length).toSeq: _*)
	
	def numberOfRequestsPerSecondAsList(data: SortedMap[Long, Seq[ResultLine]]): List[(Long, Int)] = numberOfRequestsPerSecond(data).toList

	def numberOfSuccessfulRequestsPerSecond(data: SortedMap[Long, Seq[ResultLine]]): List[(Long, Int)] = {
		numberOfRequestsPerSecondAsList(data.map(entry => entry._1 -> entry._2.filter(_.resultStatus == OK)))
	}

	def numberOfFailedRequestsPerSecond(data: SortedMap[Long, Seq[ResultLine]]): List[(Long, Int)] = {
		numberOfRequestsPerSecondAsList(data.map(entry => entry._1 -> entry._2.filter(_.resultStatus == KO)))
	}

	def numberOfRequestInResponseTimeRange(data: Seq[ResultLine], lowerBound: Int, higherBound: Int): List[(String, Int)] = {

		val groupNames = List((1, "t < " + lowerBound + "ms"), (2, lowerBound + "ms < t < " + higherBound + "ms"), (3, higherBound + "ms < t"), (4, "failed"))
		val (firstGroup, mediumGroup, lastGroup, failedGroup) = (groupNames(0), groupNames(1), groupNames(2), groupNames(3))

		var grouped = data.groupBy {
			case result if (result.resultStatus == KO) => failedGroup
			case result if (result.responseTime < lowerBound) => firstGroup
			case result if (result.responseTime > higherBound) => lastGroup
			case _ => mediumGroup
		}

		// Adds empty sections
		groupNames.map { name => grouped += (name -> grouped.getOrElse(name, Seq.empty)) }

		// Computes the number of requests per group
		// Then sorts the list by the order of the groupName
		// Then creates the list to be returned
		grouped.map(entry => (entry._1, entry._2.length)).toList.sortBy(_._1._1).map { entry => (entry._1._2, entry._2) }
	}

	def respTimeAgainstNbOfReqPerSecond(requestsPerSecond: SortedMap[Long, Int], requestData: SortedMap[Long, Seq[ResultLine]], resultStatus: ResultStatus): List[(Int, Long)] = {
		requestData.map { entry =>
			val (dateTime, list) = entry
			requestData.get(dateTime).map { list =>
				list.filter(_.resultStatus == resultStatus).map(requestsPerSecond.get(dateTime).get -> _.responseTime)
			}
		}.filter(_.isDefined).map(_.get).toList.flatten
	}

	def numberOfActiveSessionsPerSecondForAScenario(data: SortedMap[Long, Seq[ResultLine]]): List[(Long, Int)] = {
		val endsOnly = data.map(entry => entry._1 -> entry._2.filter(_.requestName == END_OF_SCENARIO))
		val startsOnly = data.map(entry => entry._1 -> entry._2.filter(_.requestName == START_OF_SCENARIO))

		var ct = 0
		SortedMap(data.map { entry =>
			val (dateTime, list) = entry
			list.foreach { result =>
				if (endsOnly.getOrElse(dateTime, List.empty).contains(result)) ct -= 1
				if (startsOnly.getOrElse(dateTime, List.empty).contains(result)) ct += 1
			}
			(dateTime, ct)
		}.toSeq: _*).toList
	}

	def numberOfActiveSessionsPerSecondByScenario(allScenarioData: Seq[(String, SortedMap[Long, Seq[ResultLine]])]): Seq[(String, List[(Long, Int)])] = {
		// Filling the map with each scenario values
		allScenarioData.map { entry =>
			val (scenarioName, scenarioData) = entry
			(scenarioName -> numberOfActiveSessionsPerSecondForAScenario(scenarioData))
		}
	}

}