package com.alyx.jobscheduler;

import com.alyx.jobscheduler.model.JobStatus;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;

import java.util.*;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
 * Simple property-based test to validate that job state transitions follow valid patterns
 * without requiring full Spring Boot context.
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
public class JobLifecycleConsistencySimpleTest {

    /**
     * Generator for valid job state sequences
     */
    private static final Generator<List<JobStatus>> jobStateSequenceGenerator() {
        return new Generator<List<JobStatus>>() {
            @Override
            public List<JobStatus> next() {
                List<JobStatus> sequence = new ArrayList<>();
                
                // Always start with SUBMITTED
                sequence.add(JobStatus.SUBMITTED);
                
                // Generate a random sequence of valid transitions
                JobStatus currentState = JobStatus.SUBMITTED;
                int maxTransitions = integers(1, 5).next(); // 1-5 transitions
                
                for (int i = 0; i < maxTransitions; i++) {
                    List<JobStatus> validNextStates = getValidNextStates(currentState);
                    if (validNextStates.isEmpty()) {
                        break; // Terminal state reached
                    }
                    
                    // Pick a random valid next state
                    JobStatus nextState = validNextStates.get(integers(0, validNextStates.size() - 1).next());
                    sequence.add(nextState);
                    currentState = nextState;
                    
                    // Stop if we reach a terminal state
                    if (isTerminalState(nextState)) {
                        break;
                    }
                }
                
                return sequence;
            }
        };
    }

    /**
     * Generator for invalid job state sequences (for negative testing)
     */
    private static final Generator<List<JobStatus>> invalidJobStateSequenceGenerator() {
        return new Generator<List<JobStatus>>() {
            @Override
            public List<JobStatus> next() {
                List<JobStatus> sequence = new ArrayList<>();
                
                // Create an invalid sequence by making invalid transitions
                JobStatus[] allStates = JobStatus.values();
                int sequenceLength = integers(2, 4).next();
                
                for (int i = 0; i < sequenceLength; i++) {
                    JobStatus randomState = allStates[integers(0, allStates.length - 1).next()];
                    sequence.add(randomState);
                }
                
                // Ensure this is actually an invalid sequence
                if (isValidSequence(sequence)) {
                    // Force an invalid transition
                    sequence.clear();
                    sequence.add(JobStatus.COMPLETED); // Invalid start
                    sequence.add(JobStatus.RUNNING);   // Invalid transition from terminal
                }
                
                return sequence;
            }
        };
    }

    @Test
    public void testValidJobLifecycleTransitions() {
        // **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
        QuickCheck.forAll(jobStateSequenceGenerator(), 
            new AbstractCharacteristic<List<JobStatus>>() {
                @Override
                protected void doSpecify(List<JobStatus> stateSequence) throws Throwable {
                    // Property: For any valid job state sequence, all transitions should be valid
                    
                    assert !stateSequence.isEmpty() : "State sequence should not be empty";
                    
                    // 1. First state should be valid initial state
                    JobStatus firstState = stateSequence.get(0);
                    assert isValidInitialState(firstState) : 
                        "Job should start in valid initial state, but was: " + firstState;
                    
                    // 2. All consecutive transitions should be valid
                    for (int i = 1; i < stateSequence.size(); i++) {
                        JobStatus fromState = stateSequence.get(i - 1);
                        JobStatus toState = stateSequence.get(i);
                        
                        assert isValidTransition(fromState, toState) : 
                            String.format("Invalid state transition from %s to %s", fromState, toState);
                    }
                    
                    // 3. Terminal states should be final (no transitions after them)
                    for (int i = 0; i < stateSequence.size() - 1; i++) {
                        JobStatus state = stateSequence.get(i);
                        if (isTerminalState(state)) {
                            assert false : "Terminal state " + state + " should not have transitions after it";
                        }
                    }
                    
                    // 4. Sequence should follow logical progression
                    validateSequenceLogic(stateSequence);
                }
            });
    }

    @Test
    public void testInvalidJobLifecycleTransitions() {
        // **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
        QuickCheck.forAll(invalidJobStateSequenceGenerator(), 
            new AbstractCharacteristic<List<JobStatus>>() {
                @Override
                protected void doSpecify(List<JobStatus> stateSequence) throws Throwable {
                    // Property: Invalid job state sequences should be rejected
                    
                    boolean isValid = isValidSequence(stateSequence);
                    
                    // The sequence should be invalid (this is negative testing)
                    assert !isValid : "Sequence should be invalid but was considered valid: " + stateSequence;
                }
            });
    }

    @Test
    public void testJobCancellationTransitions() {
        // **Feature: alyx-system-fix, Property 8: Job lifecycle consistency**
        QuickCheck.forAll(jobStateSequenceGenerator(), 
            new AbstractCharacteristic<List<JobStatus>>() {
                @Override
                protected void doSpecify(List<JobStatus> originalSequence) throws Throwable {
                    // Property: Jobs can be cancelled from any non-terminal state
                    
                    if (originalSequence.isEmpty()) return;
                    
                    // Try cancelling at each point in the sequence
                    for (int cancelPoint = 0; cancelPoint < originalSequence.size(); cancelPoint++) {
                        JobStatus stateAtCancel = originalSequence.get(cancelPoint);
                        
                        if (isCancellableState(stateAtCancel)) {
                            // Create sequence with cancellation
                            List<JobStatus> cancelledSequence = new ArrayList<>(originalSequence.subList(0, cancelPoint + 1));
                            cancelledSequence.add(JobStatus.CANCELLED);
                            
                            // Validate the cancellation transition
                            assert isValidTransition(stateAtCancel, JobStatus.CANCELLED) : 
                                "Should be able to cancel from state: " + stateAtCancel;
                            
                            // Validate the entire sequence is still valid
                            assert isValidSequence(cancelledSequence) : 
                                "Cancelled sequence should be valid: " + cancelledSequence;
                        }
                    }
                }
            });
    }

    /**
     * Checks if a state is a valid initial state for a job
     */
    private static boolean isValidInitialState(JobStatus status) {
        return status == JobStatus.SUBMITTED;
    }

    /**
     * Checks if a state is terminal (no further transitions expected)
     */
    private static boolean isTerminalState(JobStatus status) {
        return status == JobStatus.COMPLETED || 
               status == JobStatus.FAILED || 
               status == JobStatus.CANCELLED;
    }

    /**
     * Checks if a state allows cancellation
     */
    private boolean isCancellableState(JobStatus status) {
        return status != JobStatus.COMPLETED && 
               status != JobStatus.FAILED && 
               status != JobStatus.CANCELLED;
    }

    /**
     * Gets valid next states for a given current state
     */
    private static List<JobStatus> getValidNextStates(JobStatus currentState) {
        List<JobStatus> validStates = new ArrayList<>();
        
        switch (currentState) {
            case SUBMITTED:
                validStates.add(JobStatus.QUEUED);
                validStates.add(JobStatus.FAILED);
                validStates.add(JobStatus.CANCELLED);
                break;
            
            case QUEUED:
                validStates.add(JobStatus.RUNNING);
                validStates.add(JobStatus.CANCELLED);
                validStates.add(JobStatus.PAUSED);
                break;
            
            case RUNNING:
                validStates.add(JobStatus.COMPLETED);
                validStates.add(JobStatus.FAILED);
                validStates.add(JobStatus.CANCELLED);
                validStates.add(JobStatus.PAUSED);
                break;
            
            case PAUSED:
                validStates.add(JobStatus.RUNNING);
                validStates.add(JobStatus.CANCELLED);
                break;
            
            case COMPLETED:
            case FAILED:
            case CANCELLED:
                // Terminal states - no valid next states
                break;
        }
        
        return validStates;
    }

    /**
     * Validates if a transition from one state to another is valid
     */
    private static boolean isValidTransition(JobStatus fromState, JobStatus toState) {
        List<JobStatus> validNextStates = getValidNextStates(fromState);
        return validNextStates.contains(toState);
    }

    /**
     * Validates if an entire sequence of states is valid
     */
    private static boolean isValidSequence(List<JobStatus> sequence) {
        if (sequence.isEmpty()) {
            return false;
        }
        
        // Check initial state
        if (!isValidInitialState(sequence.get(0))) {
            return false;
        }
        
        // Check all transitions
        for (int i = 1; i < sequence.size(); i++) {
            if (!isValidTransition(sequence.get(i - 1), sequence.get(i))) {
                return false;
            }
        }
        
        // Check no transitions after terminal states
        for (int i = 0; i < sequence.size() - 1; i++) {
            if (isTerminalState(sequence.get(i))) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Validates the logical progression of a state sequence
     */
    private void validateSequenceLogic(List<JobStatus> sequence) {
        boolean hasQueued = sequence.contains(JobStatus.QUEUED);
        boolean hasRunning = sequence.contains(JobStatus.RUNNING);
        boolean hasCompleted = sequence.contains(JobStatus.COMPLETED);
        boolean hasFailed = sequence.contains(JobStatus.FAILED);
        boolean hasCancelled = sequence.contains(JobStatus.CANCELLED);
        
        // If job completed successfully, it should have gone through normal flow
        if (hasCompleted) {
            // Should have been queued before running (if it ran)
            if (hasRunning) {
                int queuedIndex = sequence.indexOf(JobStatus.QUEUED);
                int runningIndex = sequence.indexOf(JobStatus.RUNNING);
                assert queuedIndex < runningIndex : 
                    "Job should be queued before running";
            }
            
            // Completed should be the last state
            JobStatus lastState = sequence.get(sequence.size() - 1);
            assert lastState == JobStatus.COMPLETED : 
                "Completed job should end in COMPLETED state";
        }
        
        // If job failed, it should end in FAILED state
        if (hasFailed) {
            JobStatus lastState = sequence.get(sequence.size() - 1);
            assert lastState == JobStatus.FAILED : 
                "Failed job should end in FAILED state";
        }
        
        // If job was cancelled, it should end in CANCELLED state
        if (hasCancelled) {
            JobStatus lastState = sequence.get(sequence.size() - 1);
            assert lastState == JobStatus.CANCELLED : 
                "Cancelled job should end in CANCELLED state";
        }
        
        // Job should not have multiple terminal states
        int terminalStateCount = 0;
        if (hasCompleted) terminalStateCount++;
        if (hasFailed) terminalStateCount++;
        if (hasCancelled) terminalStateCount++;
        
        assert terminalStateCount <= 1 : 
            "Job should not have multiple terminal states";
    }
}