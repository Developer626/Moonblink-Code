/**
 * Audalyzer: an audio analyzer for Android.
 * <br>Copyright 2009-2010 Ian Cameron Smith
 *
 * <p>This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation (see COPYING).
 * 
 * <p>This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */


package org.hermit.audalyzer;



import org.hermit.android.instruments.AudioAnalyser;
import org.hermit.android.instruments.InstrumentSurface;
import org.hermit.dsp.Window;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;


/**
 * The main audio analyser view.  This class relies on the parent SurfaceRunner
 * class to do the bulk of the animation control.
 */
public class InstrumentPanel
	extends InstrumentSurface
	implements GestureDetector.OnGestureListener,
			   GestureDetector.OnDoubleTapListener
{



    // ******************************************************************** //
    // Public Constants.
    // ******************************************************************** //

    /**
     * Definitions of the available window functions.
     * 
     * These definitions determine which gauges are diplayed and
     * in which location.
     */
    public enum Instruments {
        /** Spectrum Gauge, Power and Wave. */
        SPECTRUM_P_W,
        
        /** Sonagram Gauge, Power and Wave. */
        SONAGRAM_P_W,

        /** Spectrum and Sonagram Gauge. */
        SPECTRUM_SONAGRAM,

        PITCH_ATTACK,
        
        //Full-screen layouts prob not needed any more
        
        /** Spectrum Gauge. Full-screen layout. */
        SPECTRUM,
        
        /** Sonagram Gauge. Full-screen layout. */
        SONAGRAM;

        
        public AudioAnalyser.Gauges[] getGauges() {
        	AudioAnalyser.Gauges[] arr;
        	
        	switch( this ) {
        	case SPECTRUM_P_W:
        		arr = new AudioAnalyser.Gauges[] { 
               		AudioAnalyser.Gauges.WAVEFORM_GAUGE,
       				AudioAnalyser.Gauges.SPECTRUM_GAUGE,
               		AudioAnalyser.Gauges.POWER_GAUGE
        		};
        		break;
        	case SONAGRAM_P_W:
        		arr = new AudioAnalyser.Gauges[] { 
                   	AudioAnalyser.Gauges.WAVEFORM_GAUGE,
               		AudioAnalyser.Gauges.SONAGRAM_GAUGE,
               		AudioAnalyser.Gauges.POWER_GAUGE
            	};
            	break;
        	case SPECTRUM_SONAGRAM:
        		arr = new AudioAnalyser.Gauges[] { 
       				AudioAnalyser.Gauges.SPECTRUM_GAUGE,
           			AudioAnalyser.Gauges.SONAGRAM_GAUGE
        		};
        		break;

        	case PITCH_ATTACK:
        		arr = new AudioAnalyser.Gauges[] { 
       				AudioAnalyser.Gauges.PITCH_GAUGE,
           			AudioAnalyser.Gauges.ATTACK_GAUGE
        		};
        		break;

        	// Following two fullscreen; prob not needed any more
        	case SPECTRUM:
        		arr = new AudioAnalyser.Gauges[] { 
       				AudioAnalyser.Gauges.SPECTRUM_GAUGE
        		};
        		break;            
        	case SONAGRAM:
        		arr = new AudioAnalyser.Gauges[] { 
           			AudioAnalyser.Gauges.SONAGRAM_GAUGE
            	};
        		break;
        		
        	default:
        		arr = null;
        		break;
        	}
        	
        	return arr;
        }

        
        Rect[] getPaneRects(int width, int height, int gutter) {
        	Rect[] ret= null;
        	
        	switch( this ) {
        		case SONAGRAM_P_W:
        		case SPECTRUM_P_W:
                    ret = InstrumentPanel.getLayout1( width,  height,  gutter);
                    break;
            	case PITCH_ATTACK:
        		case SPECTRUM_SONAGRAM:
                    ret = InstrumentPanel.getLayout2( width,  height,  gutter);
                    break;
        	}
            
        	return ret;
        }

        
        /**
         * Get next layout, skipping the full-screen layouts.
         * 
         * Full-screen are the last two layouts, these are skipped by the modulo.
         */
        Instruments next() {
        	int num_layouts = values().length - 2;
        	
    		return values()[ ( ordinal()+1 ) % num_layouts];
        }

        
        /**
         * Get previous layout, skipping the full-screen layouts.
         * 
         * Full-screen are the last two layouts, these are skipped by the modulo.
         */
        Instruments previous() {
        	int num_layouts = values().length - 2;

    		return values()[ ( num_layouts + ordinal() -1 ) % num_layouts ];
        }
        
    }
    	
	
    // ******************************************************************** //
    // Constructor.
    // ******************************************************************** //

	/**
	 * Create a WindMeter instance.
	 * 
	 * @param	app			The application context we're running in.
	 */
    public InstrumentPanel(Activity app) {
        super(app, SURFACE_DYNAMIC);
        context = app;
        
        audioAnalyser = new AudioAnalyser(this);
        gauge_map = audioAnalyser.getGaugeMap();
        
        addInstrument(audioAnalyser);
        
        // On-screen debug stats display.
        statsCreate(new String[] { "µs FFT", "Skip/s" });

        //Gesture detection
        gesturedetector = new GestureDetector(this);
        gesturedetector.setOnDoubleTapListener(this);
    }
    

    // ******************************************************************** //
    // Configuration.
    // ******************************************************************** //

    /**
     * Set the sample rate for this instrument.
     * 
     * @param   rate        The desired rate, in samples/sec.
     */
    public void setSampleRate(int rate) {
        audioAnalyser.setSampleRate(rate);
    }
    

    /**
     * Set the input block size for this instrument.
     * 
     * @param   size        The desired block size, in samples.
     */
    public void setBlockSize(int size) {
        audioAnalyser.setBlockSize(size);
    }
    

    /**
     * Set the spectrum analyser windowing function for this instrument.
     * 
     * @param   func        The desired windowing function.
     *                      Window.Function.BLACKMAN_HARRIS is a good option.
     *                      Window.Function.RECTANGULAR turns off windowing.
     */
    public void setWindowFunc(Window.Function func) {
        audioAnalyser.setWindowFunc(func);
    }
    

    /**
     * Set the decimation rate for this instrument.
     * 
     * @param   rate        The desired decimation.  Only 1 in rate blocks
     *                      will actually be processed.
     */
    public void setDecimation(int rate) {
        audioAnalyser.setDecimation(rate);
    }
    

    /**
     * Set the histogram averaging window for this instrument.
     * 
     * @param   rate        The averaging interval.  1 means no averaging.
     */
    public void setAverageLen(int rate) {
        audioAnalyser.setAverageLen(rate);
    }
    

    /**
     * Enable or disable stats display.
     * 
     * @param   enable        True to display performance stats.
     */
    public void setShowStats(boolean enable) {
        setDebugPerf(enable);
    }
    
    
    /**
     * Set the instruments to display
     * 
     * @param   InstrumentPanel.Intruments        Choose which ones to display.
     */
    public void setInstruments(InstrumentPanel.Instruments i) {
    	currentInstruments=i;
    	loadInstruments(currentInstruments);
    }
    

    /**
     * Load instruments
     * 
     * @param   InstrumentPanel.Intruments        Choose which ones to display.
     */
    private void loadInstruments(InstrumentPanel.Instruments i) {  	
    	Log.i(TAG, "Load instruments");
    	
    	//Stop surface update
    	onPause();
   	
		//Clear surface events
    	clearGauges();
   	
    	// Remove all current gauges
    	gauge_map.reset();
   	
    	// Create instruments, update and refresh
    	
    	Log.i(TAG, "Load gauges");
    	if ( fullScreen_instrument != null ) {
			gauge_map.addPanel( fullScreen_instrument, this );
    	} else {
    		for ( AudioAnalyser.Gauges gauge_name : i.getGauges() ) {
    			gauge_map.addPanel( gauge_name, this );
    		}
    	}

    	//Load current layout in Gauges if they're already defined
    	if ((currentWidth > 0 ) && ( currentHeight > 0 ) )
    		refreshLayout();
    	
		//Restart
    	onResume();    	

    	Log.i(TAG, "End instruments loading");    	
    }
       
       
    // ******************************************************************** //
    // Layout Processing.
    // ******************************************************************** //
   
    /**
     * Lay out the display for a given screen size.
     * 
     * @param   width       The new width of the surface.
     * @param   height      The new height of the surface.
     */
    @Override
    protected void layout(int width, int height) {
    	//Save current layout
    	currentWidth=width;
    	currentHeight=height;
    	refreshLayout();
    }
    

    /**
     * Lay out the display for the current screen size.
     */
    protected void refreshLayout() {   	
    	Log.i(TAG, "refreshLayout");    	

    	// Make up some layout parameters.
        int min = Math.min(currentWidth, currentHeight);
        int gutter = min / (min > 400 ? 15 : 20);

    	//Init
        gauge_map.clearRect();

        // Calculate the layout based on the screen configuration.
        if( isFullScreen() )
        	layoutFullscreen( currentWidth, currentHeight, gutter);
        else {
        	// Set layouts
        	
           	Rect[] panes = currentInstruments.getPaneRects( currentWidth,  currentHeight,  gutter);
        
              int index = 0;
              for ( AudioAnalyser.Gauges gauge : currentInstruments.getGauges() ) {
                  gauge_map.setRect( gauge, panes[index] );
                  ++index;
              }
    	}
        
        // Set the gauge geometries.
        gauge_map.refresh();
        
    	Log.i(TAG, "refreshLayout done");    	
    }

    
    private void layoutFullscreen(int width, int height, int gutter) {      
        int x = gutter;
        int y = gutter;

        if( fullScreen_instrument != null ) {
        	//Spectrum or Sonagram fullscreen
        	Rect fullRect = new Rect(x, y, width - gutter, height - gutter);
        	gauge_map.setRect( fullScreen_instrument, fullRect );
        	
        }
    }


    private static Rect[] getLayout1(int width, int height, int gutter) {
    	Rect [] ret =null;
    	
        int x = gutter;
        int y = gutter;
        
        boolean isPortrait = width < height;

        if ( isPortrait ) {
            // Display one column.
            int col = width - gutter * 2;
            // Divide the display into three vertical elements, the
            // spectrum or sonagram display being double-height.
            int unit = (height - gutter * 4) / 4;

            Rect pane1 = new Rect(x, y, x + col, y + unit);

            y += unit + gutter;
            Rect pane2 = new Rect(x, y, x + col, y + unit * 2);
            
            y += unit * 2 + gutter;            
            Rect pane3 = new Rect(x, y, x + col, y + unit); 
            
            ret = new Rect[] { pane1, pane2, pane3 };
        } else {
            // Divide the display into two columns.
            int col = (width - gutter * 3) / 2;        

            // Divide the left pane in two.
            int row = (height - gutter * 3) / 2;
            Rect pane1 = new Rect(x, y, x + col, y + row);

            y += row + gutter;        	
            Rect pane2 =  new Rect(x, y, x + col, height - gutter);
                
            x += col + gutter;
            Rect pane3 = new Rect(x, gutter, x + col, height - gutter);

            // Note that pane3 and pane2 are interchanged
            ret = new Rect[] { pane1, pane3, pane2 };
        }

        return ret;
    }

    
    private static Rect[] getLayout2(int width, int height, int gutter) {
    	Rect [] ret =null;
    	
        int x = gutter;
        int y = gutter;
        
        boolean isPortrait = width < height;

        if ( isPortrait ) {
            // Display one column.
            int col = width - gutter * 2;
            
            // Divide the display into two vertical elements
            int unit = (height - gutter * 3) / 2;
        	Rect pane1 = new Rect(x, y, x + col, y + unit);
        	
            y += unit + gutter;
            Rect pane2 = new Rect(x, y, x + col, y + unit);
            
            ret = new Rect[] { pane1, pane2 };
        } else {
            // Divide the display into two columns.
            int col = (width - gutter * 3) / 2;
            
        	Rect pane1 = new Rect(x, y, x + col, height - gutter);
            x += col + gutter;
            Rect pane2 = new Rect(x, y, x + col, height - gutter);
            
            ret = new Rect[] { pane1, pane2 };
        }

        return ret;
    }
    	

    
 
    private boolean isFullScreen() {
    	return fullScreen_instrument != null;
    }
    
    private boolean isPortrait() {
    	return currentWidth < currentHeight;
    }
    
    // ******************************************************************** //
    // Input Handling.
    // ******************************************************************** //

    /**
	 * Handle key input.
	 * 
     * @param	keyCode		The key code.
     * @param	event		The KeyEvent object that defines the
     * 						button action.
     * @return				True if the event was handled, false otherwise.
	 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	return false;
    }
    
    
    /**
	 * Handle touchscreen input.
	 * 
     * @param	event		The MotionEvent object that defines the action.
     * @return				True if the event was handled, false otherwise.
	 */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gesturedetector.onTouchEvent(event);
    }


   // @Override
    public boolean onDown(MotionEvent e) {
        //True for propagation to onFling Event
    	return true;
    }
    

    //@Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX,float velocityY) {
    	//if (isFullScreen)
    	//	return false;

    	final float ev1x = event1.getX();
    	//final float ev1y = event1.getY();
    	final float ev2x = event2.getX();
    	//final float ev2y = event2.getY();
    	final float xdiff = Math.abs(ev1x - ev2x);
    	//final float ydiff = Math.abs(ev1y - ev2y);
    	final float xvelocity = Math.abs(velocityX);
    	//final float yvelocity = Math.abs(velocityY);

    	if (xvelocity > this.SWIPE_MIN_VELOCITY && xdiff > this.SWIPE_MIN_DISTANCE) {
    		if (ev1x < ev2x) {
    			Log.i(TAG, "Swipe Right");
    			
    			if ( fullScreen_instrument == null ) {
        			currentInstruments = currentInstruments.next();
    			} else {
    				fullScreen_instrument = fullScreen_instrument.next();
    			}
    		} else {
    			Log.i(TAG, "Swipe Left");
    			
    			if ( fullScreen_instrument == null ) {
        			currentInstruments = currentInstruments.previous();
    			} else {
    				fullScreen_instrument = fullScreen_instrument.previous();
    			}
    		}                
			loadInstruments(currentInstruments);
    	}
    	return false;    	
    }
    

    //@Override
    public void onLongPress(MotionEvent e) {
    	//Vibrate
    	//vibrator.vibrate(100);
    	    	    	
    	final float x = e.getX();
        final float y = e.getY();
		if (fullScreen_instrument != null ) {
			//Load pref instruments
			fullScreen_instrument = null;   			
			loadInstruments(currentInstruments);
		} else {
    		//Load fullscreen instrument
			AudioAnalyser.Gauges gauge_name = gauge_map.inRect(  x,y );
			if ( gauge_name != null ) {
				fullScreen_instrument = gauge_name;
    			loadInstruments( currentInstruments );
			}
        }
    }
    

    //@Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,float distanceY) {
        return false;
    }


    //@Override
    public void onShowPress(MotionEvent e) {
    }


    //@Override
    public boolean onSingleTapUp(MotionEvent e) {

    	//Toast.makeText( context, "onSingleTapUp", Toast.LENGTH_SHORT).show();
        return false;
    }
    

    //@Override
    public boolean onDoubleTap(MotionEvent e) {
    	//Toast.makeText( context, "onDoubleTap", Toast.LENGTH_SHORT).show();
		return false;
    }
    

    //@Override
    public boolean onDoubleTapEvent(MotionEvent e) {
    	Toast.makeText( context, "onDoubleTapEvent", Toast.LENGTH_SHORT).show();
    	
		AudioAnalyser.Gauges gauge_name = gauge_map.inRect(  e.getX(), e.getY() );
		if ( gauge_name != null ) {
			Log.d(TAG, "Double tapped on: " + gauge_name.toString() );
			
			gauge_map.get( gauge_name ).switchView();
			return true;
		}
        return false;
    }
    

    //@Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
    	//Toast.makeText( context, "onSingleTapConfirmed", Toast.LENGTH_SHORT).show();
        return false;
    }
    
    
    // ******************************************************************** //
    // Save and Restore.
    // ******************************************************************** //

    /**
     * Save the state of the panel in the provided Bundle.
     * 
     * @param   icicle      The Bundle in which we should save our state.
     */
    protected void saveState(Bundle icicle) {
//      gameTable.saveState(icicle);
    }


    /**
     * Restore the panel's state from the given Bundle.
     * 
     * @param   icicle      The Bundle containing the saved state.
     */
    protected void restoreState(Bundle icicle) {
//      gameTable.pause();
//      gameTable.restoreState(icicle);
    }
    
    
    // ******************************************************************** //
    // Class Data.
    // ******************************************************************** //

    // Debugging tag.
	private static final String TAG = "Audalyzer";

	
	// ******************************************************************** //
	// Private Data.
	// ******************************************************************** //
	private Context context;
	
	//Gesture detection
	private GestureDetector gesturedetector = null;

    final private int SWIPE_MIN_DISTANCE = 100;
    final private int SWIPE_MIN_VELOCITY = 100;


    // The current Intruments in pref.
    private Instruments currentInstruments = null;

    // The gauge to show fullscreen
    private AudioAnalyser.Gauges fullScreen_instrument = null;
    
    //Current layout
    private int currentWidth=0;
    private int currentHeight=0;
    
    // Our audio input device.
    private final AudioAnalyser audioAnalyser;
    private AudioAnalyser.GaugeMap gauge_map;
}