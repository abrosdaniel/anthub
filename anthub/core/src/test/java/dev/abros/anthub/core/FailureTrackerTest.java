package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FailureTrackerTest {
 @Test void repeatedErrorsShareIdAndNeverExposeExceptionMessage(){var tracker=new FailureTracker();var ex=new RuntimeException("password=secret");var first=tracker.record("community",ex,1000);var next=tracker.record("community",ex,2000);assertTrue(first.log());assertFalse(next.log());assertEquals(first.id(),next.id());assertFalse(tracker.snapshot().toString().contains("secret"));assertTrue(tracker.record("community",ex,61000).log());}
}
