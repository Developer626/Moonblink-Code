
/**
 * org.hermit.android.instrument: graphical instruments for Android.
 * <br>Copyright 2009 Ian Cameron Smith
 * 
 * <p>These classes provide input and display functions for creating on-screen
 * instruments of various kinds in Android apps.
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


package org.hermit.android.instruments;


import org.hermit.android.core.SurfaceRunner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;


/**
 * 
 */
public class PitchGauge extends Gauge {

	// ******************************************************************** //
	// Constructor.
	// ******************************************************************** //
	
	/**
	 * Create a PitchGauge.  This constructor is package-local, as
	 * public users get these from an {@link AudioAnalyser} instrument.
	 * 
	 */
	PitchGauge(SurfaceRunner parent, int rate) {
	    super(parent);
	    nyquistFreq = rate / 2;
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
        nyquistFreq = rate / 2;
        
        // If we have a size, then we have a background.  Re-draw it
        // to show the new frequency scale.
        if (haveBounds())
            drawBg(bgCanvas, getPaint());
    }
    

    /**
     * Set the size for the label text.
     * 
     * @param   size        Label text size for the gauge.
     */
    public void setLabelSize(float size) {
        labelSize = size;
    }


    /**
     * Get the size for the label text.
     * 
     * @return              Label text size for the gauge.
     */
    public float getLabelSize() {
        return labelSize;
    }


	// ******************************************************************** //
	// Geometry.
	// ******************************************************************** //

    /**
     * This is called during layout when the size of this element has
     * changed.  This is where we first discover our size, so set
     * our geometry to match.
     * 
	 * @param	bounds		The bounding rect of this element within
	 * 						its parent View.
     */
	@Override
    public void setGeometry(Rect bounds) {
	    super.setGeometry(bounds);
        final Paint paint = getPaint();
	    
	    dispX = bounds.left;
	    dispY = bounds.top;
	    dispWidth = bounds.width();
	    dispHeight = bounds.height();
        
        // Do some layout within the meter.
        int mw = dispWidth;
        int mh = dispHeight;
        if (labelSize == 0f)
            labelSize = mw / 24f;
        
        draw_area = new Area( labelSize, 0, mw - labelSize * 2, mh - labelSize - 6);

        
        note_rect = new TextArea(
        		draw_area.left(), draw_area.top() + labelSize,
        		draw_area.width(), draw_area.height()/2, 
        		"G#", paint
        );

        offset_rect = new TextArea( 
        		draw_area.left() + draw_area.width()/4,
        		draw_area.top() + draw_area.height()/2,
        		draw_area.width()/2,
        		draw_area.height()/4, 
        		"-0.00 +/- 0.0000", paint
        );
        
        freq_rect = new TextArea( 
        		draw_area.left() + draw_area.width()/4,
        		draw_area.top() + draw_area.height()*4/5,
        		draw_area.width()/2,
        		draw_area.height()/4, 
        		"1234.56 +/- 12.34", paint
        );

        // Create the bitmap for the spectrum display,
        // and the Canvas for drawing into it.
        specBitmap = getSurface().getBitmap(dispWidth, dispHeight);
        specCanvas = new Canvas(specBitmap);
        
        // Create the bitmap for the background,
        // and the Canvas for drawing into it.
        bgBitmap = getSurface().getBitmap(dispWidth, dispHeight);
        bgCanvas = new Canvas(bgBitmap);
        
        drawBg(bgCanvas, paint);
	}


    // ******************************************************************** //
    // Background Drawing.
    // ******************************************************************** //
    
    /**
     * Do the subclass-specific parts of drawing the background
     * for this element.  Subclasses should override
     * this if they have significant background content which they would
     * like to draw once only.  Whatever is drawn here will be saved in
     * a bitmap, which will be rendered to the screen before the
     * dynamic content is drawn.
     * 
     * <p>Obviously, if implementing this method, don't clear the screen when
     * drawing the dynamic part.
     * 
     * @param   canvas      Canvas to draw into.
     * @param   paint       The Paint which was set up in initializePaint().
     */
    private void drawBg(Canvas canvas, Paint paint) {
        canvas.drawColor(0xff000000);
        
        paint.setColor(0xffffff00);
        paint.setStyle(Style.STROKE);

        canvas.drawRect(draw_area.left(), draw_area.top(), draw_area.width()-1, draw_area.height()-1, paint);
    }


    // ******************************************************************** //
    // Data Updates.
    // ******************************************************************** //
    
    public boolean needFFT() { return true; }
    
    public void setFFT(float[] spectrumData) {
    	update(spectrumData);
    }   
    
    
	/**
	 * New data from the instrument has arrived.  This method is called
	 * on the thread of the instrument.
	 * 
     * @param   data        An array of floats defining the signal power
     *                      at each frequency in the spectrum.
	 */
	final void update(float[] data) {
        final Canvas canvas = specCanvas;
        final Paint paint = getPaint();
        
        // Now actually do the drawing.
        synchronized (this) {
            canvas.drawBitmap(bgBitmap, 0, 0, paint);
            
            showPitch(data, canvas, paint);
        }
    }

	   
        
	/**
	 */
	private void showPitch(float[] data, Canvas canvas, Paint paint) {
		final int len = data.length;
		final float lf = 1f *nyquistFreq / len;
		
        paint.setStyle(Style.FILL);

        float pitch = detectPitch( data);
        DisplayNote closest_note = new DisplayNote( 1f, "");
        
    	closest_frequency( closest_note, pitch, lf);
    	note_rect.draw( closest_note.disp_note, canvas, paint);

    	
        String offs_str = String.format("%.2f +/- %.4f", closest_note.note_offset, closest_note.note_err );
    	offset_rect.draw( offs_str, canvas, paint);

        String freq_str = String.format("%.2f +/- %.2f", pitch, lf/2);
    	freq_rect.draw( freq_str, canvas, paint);
	}

	
	
	public float detectPitch( float[] fft_data ) {

		final int len = fft_data.length;
		final float lf = 1f *nyquistFreq / len;
		//final float rf = 1f* nyquistFreq;

		//Log.d(TAG, "nyquistFreq, lf: " + nyquistFreq + "; " + lf);
		//Log.d(TAG, "fft_data length: " + len );

		// Min and Max index to check
		float min_freq = MIN_FREQUENCY;
		float max_freq = MAX_FREQUENCY;

		int min_index_fft = Math.round( min_freq/lf );
		int max_index_fft = Math.round( max_freq/lf );

		// Guard index ranges for input array
		if ( max_index_fft >= fft_data.length ) {
			max_index_fft = fft_data.length -1;
			max_freq = lf*max_index_fft;
		}

		//Log.d(TAG, "Min, Max index: " + min_index_fft + "; " + max_index_fft);

		double best_frequency = min_index_fft;
		double best_amplitude =	 0;

		final double norm_factor = Math.pow(min_freq * max_freq, 0.5);

		for (int i = min_index_fft; i <= max_index_fft; i++) {

			double frequency = i * lf;
			float  amplitude = fft_data[i];

			double normalized_amplitude = norm_factor * amplitude / frequency;

			if (normalized_amplitude > best_amplitude) {
				best_frequency = frequency;
				best_amplitude = normalized_amplitude;
			}
		}

		//Log.d(TAG, "best_frequency: " + best_frequency + "; best_amplitude:" + best_amplitude );

		return (float) best_frequency;
	}


	// ******************************************************************** //
	// View Drawing.
	// ******************************************************************** //
	
	/**
	 * Do the subclass-specific parts of drawing for this element.
	 * This method is called on the thread of the containing SuraceView.
	 * 
	 * <p>Subclasses should override this to do their drawing.
	 * 
	 * @param	canvas		Canvas to draw into.
	 * @param	paint		The Paint which was set up in initializePaint().
     * @param   now         Nominal system time in ms. of this update.
	 */
	@Override
    protected final void drawBody(Canvas canvas, Paint paint, long now) {
	    // Since drawBody may be called more often than we get audio
	    // data, it makes sense to just draw the buffered image here.
	    synchronized (this) {
	        canvas.drawBitmap(specBitmap, dispX, dispY, null);
	    }
	}
	

	// ******************************************************************** //
	// Class Data.
	// ******************************************************************** //


	// Min and max frequency to check in Hz
	// These are low and high A's
	private final static int MIN_FREQUENCY = 55;
	private final static int MAX_FREQUENCY = 5000;


	// Source: http://www.phy.mtu.edu/~suits/notefreqs.html
	private static final String[][] StandardNotes = { 
		 //Display  Note 	Frequency (Hz)
		 { "C", "C0", "16.35" },
		 { "C#", "C#0/Db0", "17.32" },
		 { "D", "D0", "18.35" },
		 { "D#", "D#0/Eb0", "19.45" },
		 { "E", "E0", "20.60" },
		 { "F", "F0", "21.83" },
		 { "F#", "F#0/Gb0", "23.12" },
		 { "G", "G0", "24.50" },
		 { "G#", "G#0/Ab0", "25.96" },
		 { "A", "A0", "27.50" },
		 { "A#", "A#0/Bb0", "29.14" },
		 { "B", "B0", "30.87" },
		 { "C", "C1", "32.70" },
		 { "C#", "C#1/Db1", "34.65" },
		 { "D", "D1", "36.71" },
		 { "D#", "D#1/Eb1", "38.89" },
		 { "E", "E1", "41.20" },
		 { "F", "F1", "43.65" },
		 { "F#", "F#1/Gb1", "46.25" },
		 { "G", "G1", "49.00" },
		 { "G#", "G#1/Ab1", "51.91" },
		 { "A", "A1", "55.00" },
		 { "A#", "A#1/Bb1", "58.27" },
		 { "B", "B1", "61.74" },
		 { "C", "C2", "65.41" },
		 { "C#", "C#2/Db2", "69.30" },
		 { "D", "D2", "73.42" },
		 { "D#", "D#2/Eb2", "77.78" },
		 { "E", "E2", "82.41" },
		 { "F", "F2", "87.31" },
		 { "F#", "F#2/Gb2", "92.50" },
		 { "G", "G2", "98.00" },
		 { "G#", "G#2/Ab2", "103.83" },
		 { "A", "A2", "110.00" },
		 { "A#", "A#2/Bb2", "116.54" },
		 { "B", "B2", "123.47" },
		 { "C", "C3", "130.81" },
		 { "C#", "C#3/Db3", "138.59" },
		 { "D", "D3", "146.83" },
		 { "D#", "D#3/Eb3", "155.56" },
		 { "E", "E3", "164.81" },
		 { "F", "F3", "174.61" },
		 { "F#", "F#3/Gb3", "185.00" },
		 { "G", "G3", "196.00" },
		 { "G#", "G#3/Ab3", "207.65" },
		 { "A", "A3", "220.00" },
		 { "A#", "A#3/Bb3", "233.08" },
		 { "B", "B3", "246.94" },
		 { "C", "C4", "261.63" },
		 { "C#", "C#4/Db4", "277.18" },
		 { "D", "D4", "293.66" },
		 { "D#", "D#4/Eb4", "311.13" },
		 { "E", "E4", "329.63" },
		 { "F", "F4", "349.23" },
		 { "F#", "F#4/Gb4", "369.99" },
		 { "G", "G4", "392.00" },
		 { "G#", "G#4/Ab4", "415.30" },
		 { "A", "A4", "440.00" },
		 { "A#", "A#4/Bb4", "466.16" },
		 { "B", "B4", "493.88" },
		 { "C", "C5", "523.25" },
		 { "C#", "C#5/Db5", "554.37" },
		 { "D", "D5", "587.33" },
		 { "D#", "D#5/Eb5", "622.25" },
		 { "E", "E5", "659.26" },
		 { "F", "F5", "698.46" },
		 { "F#", "F#5/Gb5", "739.99" },
		 { "G", "G5", "783.99" },
		 { "G#", "G#5/Ab5", "830.61" },
		 { "A", "A5", "880.00" },
		 { "A#", "A#5/Bb5", "932.33" },
		 { "B", "B5", "987.77" },
		 { "C", "C6", "1046.50" },
		 { "C#", "C#6/Db6", "1108.73" },
		 { "D", "D6", "1174.66" },
		 { "D#", "D#6/Eb6", "1244.51" },
		 { "E", "E6", "1318.51" },
		 { "F", "F6", "1396.91" },
		 { "F#", "F#6/Gb6", "1479.98" },
		 { "G", "G6", "1567.98" },
		 { "G#", "G#6/Ab6", "1661.22" },
		 { "A", "A6", "1760.00" },
		 { "A#", "A#6/Bb6", "1864.66" },
		 { "B", "B6", "1975.53" },
		 { "C", "C7", "2093.00" },
		 { "C#", "C#7/Db7", "2217.46" },
		 { "D", "D7", "2349.32" },
		 { "D#", "D#7/Eb7", "2489.02" },
		 { "E", "E7", "2637.02" },
		 { "F", "F7", "2793.83" },
		 { "F#", "F#7/Gb7", "2959.96" },
		 { "G", "G7", "3135.96" },
		 { "G#", "G#7/Ab7", "3322.44" },
		 { "A", "A7", "3520.00" },
		 { "A#", "A#7/Bb7", "3729.31" },
		 { "B", "B7", "3951.07" },
		 { "C", "C8", "4186.01" },
		 { "C#", "C#8/Db8", "4434.92" },
		 { "D", "D8", "4698.64" },
		 { "D#", "D#8/Eb8", "4978.03" }
	};
	

	private class DisplayNote {
		public float  frequency;
		public String disp_note;
		public float  note_offset;
		public float  note_err;
		
		public DisplayNote(float freq, String disp) {
			frequency = freq;
			disp_note = disp;
			
			note_offset = 0f;
			note_err    = 0f;
		}
	}

	private static DisplayNote[] display_notes;
	
	// Class Initializer
	{
		// Instantiate the array of frequencies to display
		display_notes = new DisplayNote[StandardNotes.length ];
		
		for( int i = 0; i < StandardNotes.length; ++ i) {
			display_notes[i] = new DisplayNote( Float.parseFloat( StandardNotes[i][2] ), StandardNotes[i][0] );
		}
	}
	
	
	private static void closest_frequency( DisplayNote out, float frequency, float lf) {
		int min_i = 0;
		int max_i = display_notes.length - 1;
		
		// Do bounds checking
		
		if ( frequency < display_notes[0].frequency ) {
			out.frequency = frequency - display_notes[0].frequency;
			out.disp_note = "-" + display_notes[0].disp_note;
			return;
		}
		
		if ( frequency > display_notes[max_i].frequency ) {
			out.frequency = display_notes[max_i].frequency - frequency;
			out.disp_note = "+" + display_notes[max_i].disp_note;
			return;
		}
		
		// Do a binary search on the best match for given frequency
		while ( min_i < max_i -1 ) {
			int i = (min_i + max_i)/2;
			
			if ( frequency >= display_notes[i].frequency && frequency < display_notes[i + 1 ].frequency ) {
				min_i = i;
				break;
			}
			
			if ( frequency < display_notes[i].frequency ) {
				max_i = i;
			} else if ( frequency > display_notes[i].frequency ) {
				min_i = i;
			}
		}
		
		float freq0 = display_notes[min_i].frequency;
		float freq1 = display_notes[min_i+1].frequency;
		
		// Calculate offset to defined notes on the note scale
		double note_diff = (log2(frequency) - log2(freq0) )/( log2(freq1) - log2(freq0) );

    	// Determine the approximate error interval for the note
    	double err_top    = log2(frequency + lf/2) - log2(freq0)/( log2(freq1) - log2(freq0) );
    	double err_bottom = log2(frequency - lf/2) - log2(freq0)/( log2(freq1) - log2(freq0) );

    	// Note that the error interval is asymmetric around the frequency.
    	// So following is an approximation
    	double err = (err_top - err_bottom)/2;
		
		// NOTE: Frequency field is abused to pass the note offset
		if ( note_diff <= 0.5 ) {
			out.note_offset = (float) note_diff;
			out.note_err = (float) err;
			out.frequency   = freq0;
			out.disp_note   = display_notes[min_i].disp_note;
		} else {
			out.note_offset = (float) -( 1.0 - note_diff );
			out.frequency   = freq1;
			out.disp_note   = display_notes[min_i + 1].disp_note;
		}
		
	}

	//
	// Copied from SpectrumGauge
	//
	
	// Log of 2.
	private static final double LOG2 = Math.log(2);
    private static final double log2(double x) {
        return Math.log(x) / LOG2;
    }
    
	// ******************************************************************** //
	// Private Data.
	// ******************************************************************** //

    // The Nyquist frequency -- the highest frequency
    // represented in the spectrum data we will be plotting.
    private int nyquistFreq = 0;

    
	// Display position and size within the parent view.
    private int dispX = 0;
    private int dispY = 0;
	private int dispWidth = 0;
	private int dispHeight = 0;
    
    // Label text size for the gauge.  Zero means not set yet.
    private float labelSize = 0f;

    private Area draw_area;
    private TextArea note_rect;
    private TextArea freq_rect;
    private TextArea offset_rect;
    
    // Bitmap in which we draw the gauge background,
    // and the Canvas and Paint for drawing into it.
    private Bitmap bgBitmap = null;
    private Canvas bgCanvas = null;

    // Bitmap in which we draw the audio spectrum display,
    // and the Canvas and Paint for drawing into it.
    private Bitmap specBitmap = null;
    private Canvas specCanvas = null;    
}