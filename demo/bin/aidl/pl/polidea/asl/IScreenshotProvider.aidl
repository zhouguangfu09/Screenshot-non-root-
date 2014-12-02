// AIDL file specifying interface used by clients to retrieve screenshots

package pl.polidea.asl;


// Interface for fetching screenshots
interface IScreenshotProvider {
	boolean isAvailable();

	// Create a screen snapshot and returns path to file where it is written.
	String takeScreenshot();
	byte[] writeImageOutputStream();
}