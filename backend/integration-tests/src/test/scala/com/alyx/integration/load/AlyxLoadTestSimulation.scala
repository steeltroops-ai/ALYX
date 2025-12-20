package com.alyx.integration.load

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Gatling load test simulation for ALYX system.
 * Tests system performance with 400+ concurrent users.
 * 
 * Requirements: 4.1, 3.4, 2.1, 5.5
 */
class AlyxLoadTestSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling Load Test")

  // Authentication scenario
  val authenticate = exec(
    http("authenticate")
      .post("/api/auth/login")
      .body(StringBody("""{"email": "physicist${userId}@alyx.org", "password": "test_password", "role": "PHYSICIST"}"""))
      .check(jsonPath("$.token").saveAs("authToken"))
  )

  // Job submission scenario
  val submitAnalysisJob = exec(
    http("submit_analysis_job")
      .post("/api/jobs/submit")
      .header("Authorization", "Bearer ${authToken}")
      .body(StringBody(session => {
        val energyMin = 1000 + Random.nextInt(2000)
        val energyMax = energyMin + 1000 + Random.nextInt(2000)
        val cores = 4 + Random.nextInt(28)
        val memory = 8 + Random.nextInt(56)
        
        s"""
        {
          "analysisType": "PARTICLE_RECONSTRUCTION",
          "parameters": {
            "energyRange": {"min": $energyMin, "max": $energyMax},
            "detectorRegions": ["CENTRAL", "FORWARD"],
            "timeWindow": {"start": "2024-01-01T00:00:00Z", "end": "2024-01-01T01:00:00Z"}
          },
          "priority": "NORMAL",
          "resourceRequirements": {
            "cores": $cores,
            "memoryGB": $memory,
            "estimatedDurationMinutes": ${15 + Random.nextInt(45)}
          }
        }
        """
      }))
      .check(status.is(201))
      .check(jsonPath("$.jobId").saveAs("jobId"))
  )

  // Job status monitoring scenario
  val monitorJobStatus = repeat(5, "statusCheck") {
    exec(
      http("check_job_status")
        .get("/api/jobs/${jobId}/status")
        .header("Authorization", "Bearer ${authToken}")
        .check(status.is(200))
        .check(jsonPath("$.status").saveAs("jobStatus"))
    )
    .pause(2.seconds)
  }

  // Query execution scenario
  val executeQuery = exec(
    http("execute_query")
      .get("/api/data/events/search")
      .queryParam("energyMin", session => 1000 + Random.nextInt(3000))
      .queryParam("energyMax", session => 4000 + Random.nextInt(2000))
      .queryParam("limit", "100")
      .header("Authorization", "Bearer ${authToken}")
      .check(status.is(200))
      .check(responseTimeInMillis.lte(2000)) // Requirement 3.4: 2 second response time
  )

  // Visualization data request scenario
  val requestVisualizationData = exec(
    http("request_visualization")
      .get("/api/visualization/events/sample")
      .queryParam("count", "10")
      .header("Authorization", "Bearer ${authToken}")
      .check(status.is(200))
      .check(responseTimeInMillis.lte(2000)) // Requirement 2.1: 2 second rendering time
  )

  // Collaboration session scenario
  val joinCollaborationSession = exec(
    http("create_collaboration_session")
      .post("/api/collaboration/sessions")
      .header("Authorization", "Bearer ${authToken}")
      .body(StringBody("""
      {
        "sessionName": "Load Test Session ${userId}",
        "analysisType": "COLLABORATIVE_RECONSTRUCTION",
        "maxParticipants": 5
      }
      """))
      .check(status.is(201))
      .check(jsonPath("$.sessionId").saveAs("sessionId"))
  )
  .exec(
    http("update_session_parameters")
      .put("/api/collaboration/sessions/${sessionId}/parameters")
      .header("Authorization", "Bearer ${authToken}")
      .body(StringBody(session => {
        val paramValue = Random.nextInt(10000)
        s"""
        {
          "parameterId": "energy_threshold_${session("userId").as[String]}",
          "value": $paramValue,
          "userId": "${session("userId").as[String]}"
        }
        """
      }))
      .check(status.is(200))
  )

  // Data pipeline scenario
  val executePipeline = exec(
    http("execute_data_pipeline")
      .post("/api/data/pipeline/execute")
      .header("Authorization", "Bearer ${authToken}")
      .body(StringBody("""
      {
        "pipelineType": "FULL_RECONSTRUCTION",
        "inputFilter": {
          "energyRange": {"min": 1000, "max": 5000},
          "timeWindow": "PT1H"
        },
        "outputFormat": "PHYSICS_ANALYSIS"
      }
      """))
      .check(status.is(202))
      .check(jsonPath("$.pipelineId").saveAs("pipelineId"))
  )

  // Health check scenario
  val healthCheck = exec(
    http("health_check")
      .get("/actuator/health")
      .check(status.is(200))
  )

  // User scenarios
  val physicistUser = scenario("Physicist User")
    .feed(Iterator.from(1).map(i => Map("userId" -> i)))
    .exec(authenticate)
    .pause(1.second)
    .exec(submitAnalysisJob)
    .pause(2.seconds)
    .exec(monitorJobStatus)
    .pause(1.second)
    .exec(executeQuery)
    .pause(1.second)
    .exec(requestVisualizationData)

  val collaborativeUser = scenario("Collaborative User")
    .feed(Iterator.from(1000).map(i => Map("userId" -> i)))
    .exec(authenticate)
    .pause(1.second)
    .exec(joinCollaborationSession)
    .pause(2.seconds)
    .repeat(3) {
      exec(executeQuery)
      .pause(1.second)
    }

  val dataAnalysisUser = scenario("Data Analysis User")
    .feed(Iterator.from(2000).map(i => Map("userId" -> i)))
    .exec(authenticate)
    .pause(1.second)
    .exec(executePipeline)
    .pause(3.seconds)
    .exec(executeQuery)
    .pause(1.second)
    .exec(requestVisualizationData)

  val systemMonitor = scenario("System Monitor")
    .repeat(100) {
      exec(healthCheck)
      .pause(5.seconds)
    }

  // Load test setup - 400+ concurrent users
  setUp(
    // Primary load: 300 physicist users
    physicistUser.inject(
      rampUsers(300).during(2.minutes),
      constantUsersPerSec(50).during(5.minutes)
    ),
    
    // Collaboration load: 80 collaborative users
    collaborativeUser.inject(
      rampUsers(80).during(1.minute),
      constantUsersPerSec(15).during(5.minutes)
    ),
    
    // Data analysis load: 50 data analysis users
    dataAnalysisUser.inject(
      rampUsers(50).during(1.minute),
      constantUsersPerSec(8).during(5.minutes)
    ),
    
    // System monitoring: 5 monitor users
    systemMonitor.inject(
      rampUsers(5).during(30.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     // System should handle the load successfully
     global.responseTime.max.lt(5000), // Max response time under 5 seconds
     global.responseTime.mean.lt(2000), // Mean response time under 2 seconds
     global.successfulRequests.percent.gt(95), // 95% success rate minimum
     
     // Specific performance requirements
     forAll.responseTime.percentile3.lt(3000), // 99th percentile under 3 seconds
     
     // Query performance (Requirement 3.4)
     details("execute_query").responseTime.percentile4.lt(2000), // 99.9% under 2 seconds
     
     // Visualization performance (Requirement 2.1)
     details("request_visualization").responseTime.max.lt(2000), // Max 2 seconds
     
     // Collaboration responsiveness (Requirement 5.5)
     details("update_session_parameters").responseTime.mean.lt(1000) // Sub-second response
   )
}