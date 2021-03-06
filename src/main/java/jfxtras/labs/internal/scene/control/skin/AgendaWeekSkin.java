/**
 * Copyright (c) 2011, JFXtras
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *	 * Redistributions of source code must retain the above copyright
 *	   notice, this list of conditions and the following disclaimer.
 *	 * Redistributions in binary form must reproduce the above copyright
 *	   notice, this list of conditions and the following disclaimer in the
 *	   documentation and/or other materials provided with the distribution.
 *	 * Neither the name of the <organization> nor the
 *	   names of its contributors may be used to endorse or promote products
 *	   derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// TODO: refactor the layout code in showMenu
// TODO: manual edit of all properties, location, description
// TODO: concept of tasks; appointment with only a start date (not whole day). Rendered as a line. 
// TODO: dropping an area event in the header and then back into the day; take the location of the drop into account as the start time (instead of the last start time)
// TODO: allow dragging on day spanning events on the not-the-first areas
// TODO: undo feature on all actions (remove, add, ...)
// TODO: single day view
// TODO: reminders?
// TODO: callbacks to check if a delete is ok, etc
package jfxtras.labs.internal.scene.control.skin;

import java.awt.BorderLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.ScrollPaneBuilder;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import jfxtras.labs.animation.Timer;
import jfxtras.labs.internal.scene.control.behavior.AgendaBehavior;
import jfxtras.labs.scene.control.Agenda;
import jfxtras.labs.scene.control.Agenda.Appointment;
import jfxtras.labs.scene.control.CalendarTextField;
import jfxtras.labs.util.NodeUtil;

import com.sun.javafx.scene.control.skin.SkinBase;

/**
 * @author Tom Eugelink
 */
public class AgendaWeekSkin extends SkinBase<Agenda, AgendaBehavior>
{
	// ==================================================================================================================
	// CONSTRUCTOR
	
	/**
	 * 
	 */
	public AgendaWeekSkin(Agenda control)
	{
		super(control, new AgendaBehavior(control));
		construct();
	}

	/*
	 * construct the component
	 */
	private void construct()
	{	
		// setup component
		createNodes();

		// react to changes in the locale 
		getSkinnable().localeProperty().addListener(new InvalidationListener() 
		{
			@Override
			public void invalidated(Observable observable)
			{
				refreshLocale();
			} 
		});
		refreshLocale();
		
		// react to changes in the appointments 
		getSkinnable().displayedCalendar().addListener(new InvalidationListener()
		{			
			@Override
			public void invalidated(Observable observable)
			{
				assignCalendarToTheDayPanes();
				setupAppointments();
			}
		});
		assignCalendarToTheDayPanes();
		
		// react to changes in the appointments 
		getSkinnable().appointments().addListener(new ListChangeListener<Agenda.Appointment>() 
		{
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends Appointment> arg0)
			{
				setupAppointments();
			} 
		});
		setupAppointments();
		
		// react to changes in the appointments 
		getSkinnable().selectedAppointments().addListener(new ListChangeListener<Agenda.Appointment>() 
		{
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends Appointment> changes)
			{
				// update the styleclass
				for (DayPane lDayPane : weekPane.dayPanes)
				{
					for (AbstractAppointmentPane lAppointmentPane : lDayPane.collectAllPanes())
					{
						// remove 
						if ( lAppointmentPane.getStyleClass().contains("Selected") == true
						  && getSkinnable().selectedAppointments().contains(lAppointmentPane.appointment) == false
						   )
						{
							lAppointmentPane.getStyleClass().remove("Selected");
						}
						// add
						if ( lAppointmentPane.getStyleClass().contains("Selected") == false
						  && getSkinnable().selectedAppointments().contains(lAppointmentPane.appointment) == true
						   )
						{
							lAppointmentPane.getStyleClass().add("Selected");
						}
					}
				}
			} 
		});
	}
	
	/**
	 * Assign a calendar to each day, so it knows what it must draw.
	 * 
	 */
	private void assignCalendarToTheDayPanes()
	{
		// get the first day of week calendar
		Calendar lCalendar = getFirstDayOfWeekCalendar();
		Calendar lStartCalendar = (Calendar)lCalendar.clone();
		
		// assign it
		Calendar lEndCalendar = null;
		for (int i = 0; i < 7; i++)
		{
			weekPane.dayPanes.get(i).calendarObjectProperty.set( (Calendar)lCalendar.clone() );
			if (i== 6) lEndCalendar = (Calendar)lCalendar.clone();
			lCalendar.add(Calendar.DATE, 1);
		}		
		
		// tell the skin what range is needed
		if (getSkinnable().getCalendarRangeCallback() != null)
		{
			Agenda.CalendarRange lCalendarRange = new Agenda.CalendarRange(lStartCalendar, lEndCalendar);
			getSkinnable().getCalendarRangeCallback().call(lCalendarRange);
		}
	}
	
	/**
	 * 
	 */
	private void refreshLocale()
	{
		// create the formatter to use
		iDayOfWeekDateFormat = (SimpleDateFormat)SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT, getSkinnable().getLocale());
		iDayOfWeekDateFormat.applyPattern("E");
		iDateFormat = (SimpleDateFormat)SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT, getSkinnable().getLocale());
		
		// force redraw the dayHeaders by reassigning the calendar
		for (DayPane lDay : weekPane.dayPanes)
		{
			if (lDay.calendarObjectProperty.get() != null)
			{
				lDay.calendarObjectProperty.set( (Calendar)lDay.calendarObjectProperty.get().clone() );
			}
		}
	}
	private SimpleDateFormat iDayOfWeekDateFormat = null;
	private SimpleDateFormat iDateFormat = null;
	private SimpleDateFormat iTimeFormat = new SimpleDateFormat("hh:mm");

	/**
	 * Have all days reconstruct the appointments
	 */
	private void setupAppointments()
	{
		calculateSizes();
		for (DayPane lDay : weekPane.dayPanes)
		{
			lDay.setupAppointments();
		}
		nowUpdateRunnable.run(); // place the now line
	}
	
	// ==================================================================================================================
	// PROPERTIES
	
	// ==================================================================================================================
	// DRAW
	
	/**
	 * construct the nodes
	 */
	private void createNodes()
	{
		// we use a borderpane
		borderPane = new BorderPane();
		
		// center
		weekPane = new WeekPane();
		weekScrollPane = ScrollPaneBuilder.create()
			.prefWidth(getSkinnable().getWidth())
			.prefHeight(getSkinnable().getHeight())
			.layoutY(50)
			.content(weekPane)
			.hbarPolicy(ScrollBarPolicy.NEVER)
			.pannable(false)
			.build();
		borderPane.setCenter(weekScrollPane);
		// bind the size of the week to the scrollpane's viewport
		weekScrollPane.viewportBoundsProperty().addListener(new InvalidationListener()
		{
			@Override
			public void invalidated(Observable viewportBoundsProperty)
			{
				calculateSizes();
				nowUpdateRunnable.run();
			}
		});
		
		// top: header have to be created after the week, because there is a binding to days
		weekHeaderPane = new WeekHeaderPane();
		weekHeaderPane.setTranslateX(1); // correct for the scrollpane
		borderPane.setTop(weekHeaderPane);
		
		// add to self
		this.getStyleClass().add(this.getClass().getSimpleName()); // always add self as style class, because CSS should relate to the skin not the control
		dragPane = new Pane();
		dragPane.prefWidthProperty().bind(widthProperty());
		dragPane.prefHeightProperty().bind(heightProperty());
		dragPane.getChildren().add(borderPane);
		borderPane.prefWidthProperty().bind(dragPane.widthProperty());
		borderPane.prefHeightProperty().bind(dragPane.heightProperty());
		getChildren().add(dragPane);
		
		// close icon
		closeIconImage = new Image(this.getClass().getResourceAsStream(this.getClass().getSimpleName() + "PopupCloseWindowIcon.png"));
	}
	Pane dragPane = null;
	BorderPane borderPane = null;
	WeekHeaderPane weekHeaderPane = null;
	ScrollPane weekScrollPane = null;
	WeekPane weekPane = null;
	private Image closeIconImage = null;

	// ==================================================================================================================
	// PANES
	
	/**
	 * Responsible for rendering the days
	 */
	class WeekHeaderPane extends Pane
	{
		/**
		 * 
		 */
		public WeekHeaderPane()
		{
			// 7 days per week
			for (int i = 0; i < 7; i++)
			{
				DayHeaderPane lDayHeader = new DayHeaderPane(weekPane.dayPanes.get(i)); // associate with a day, so we can use its administration. This needs only be done once
				lDayHeader.layoutXProperty().bind(weekPane.dayPanes.get(i).layoutXProperty());			
				lDayHeader.layoutYProperty().set(0);
				lDayHeader.prefWidthProperty().bind(weekPane.dayPanes.get(i).prefWidthProperty());			
				lDayHeader.prefHeightProperty().bind(heightProperty());			
				getChildren().add(lDayHeader);
				dayHeaderPanes.add(lDayHeader);
			}
		}
		final List<DayHeaderPane> dayHeaderPanes = new ArrayList<DayHeaderPane>();
	}

	/**
	 * Responsible for rendering the day header (whole day appointments).
	 * This class is connected to the day and uses its data.
	 */
	class DayHeaderPane extends Pane
	{
		public DayHeaderPane(DayPane dayPane)
		{
			// for debugging setStyle("-fx-border-color:PINK;-fx-border-width:4px;");
			getStyleClass().add("DayHeader");
			
			// link up the two panes
			this.dayPane = dayPane;
			dayPane.dayHeader = this; // two way link
			
			// set label
			final int lPadding = 3;
			calendarText = new Text("?");
			Rectangle lClip = new Rectangle(0,0,0,0);
			lClip.widthProperty().bind(widthProperty().subtract(lPadding));
			lClip.heightProperty().bind(heightProperty());
			calendarText.setClip(lClip);
			getChildren().add(calendarText);
			dayPane.calendarObjectProperty.addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					if (DayHeaderPane.this.dayPane.calendarObjectProperty.get() == null) return;
					String lLabel = iDayOfWeekDateFormat.format(DayHeaderPane.this.dayPane.calendarObjectProperty.get().getTime()) + " " + iDateFormat.format(DayHeaderPane.this.dayPane.calendarObjectProperty.get().getTime());
					calendarText.setText(lLabel);
					double lX = (dayWidth - calendarText.prefWidth(0)) / 2;
					calendarText.setX( lX < 0 ? lPadding : lX + lPadding );
					calendarText.setY(calendarText.prefHeight(0));
				}
			});
			
			// change the layout related to the size
			widthProperty().addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					relayout();
				}
			});
			heightProperty().addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					relayout();
				}
			});
			
			// layout
			relayout();
		}
		DayPane dayPane = null;
		Text calendarText = null;
		
		/**
		 * 
		 */
		public void relayout()
		{
			// create headers
			int lOffset = maxNumberOfWholedayAppointments - appointmentHeaderPanes.size(); // to make sure the appointments are renders aligned bottom
			for (AppointmentHeaderPane lAppointmentHeaderPane : appointmentHeaderPanes)
			{
				int lIdx = appointmentHeaderPanes.indexOf(lAppointmentHeaderPane);
				lAppointmentHeaderPane.setLayoutX(lIdx * wholedayAppointmentWidth);
				lAppointmentHeaderPane.setLayoutY( titleCalendarHeight + ((lIdx + lOffset) * wholedayTitleHeight) );
				lAppointmentHeaderPane.setPrefSize(dayWidth - (lIdx * wholedayAppointmentWidth), (appointmentHeaderPanes.size() - lIdx) * wholedayTitleHeight);
			}
		}
		
		/**
		 * 
		 */
		public void setupAppointments()
		{
			// create headers
			getChildren().removeAll(appointmentHeaderPanes);
			appointmentHeaderPanes.clear();
			for (AppointmentPane lAppointmentPane : dayPane.wholedayAppointmentPanes)
			{
				AppointmentHeaderPane lAppointmentHeaderPane = new AppointmentHeaderPane(lAppointmentPane.appointment);
				getChildren().add(lAppointmentHeaderPane);				
				appointmentHeaderPanes.add(lAppointmentHeaderPane);	
			}
			
			// and layout
			relayout();
		}
		final List<AppointmentHeaderPane> appointmentHeaderPanes = new ArrayList<AgendaWeekSkin.AppointmentHeaderPane>();		
	}
	
	/**
	 * Responsible for rendering a single whole day appointment on a day header.
	 * 
	 */
	class AppointmentHeaderPane extends AbstractAppointmentPane
	{
		/**
		 * 
		 * @param calendar
		 * @param appointment
		 */
		public AppointmentHeaderPane(Agenda.Appointment appointment)
		{
			super(appointment);
			
			// for debugging setStyle("-fx-border-color:GREEN;-fx-border-width:4px;");
			getStyleClass().add("Appointment");
			getStyleClass().add(appointment.getAppointmentGroup().getStyleClass());

			// add a text node
			Text lSummaryText = new Text(appointment.getSummary());
			lSummaryText.getStyleClass().add("AppointmentLabel");
			lSummaryText.setX( padding );
			lSummaryText.setY( textHeight1M );
			Rectangle lClip = new Rectangle(0,0,0,0);
			lClip.widthProperty().bind(widthProperty().subtract(padding));
			lClip.heightProperty().bind(heightProperty());
			lSummaryText.setClip(lClip);
			getChildren().add(lSummaryText);			
			
			// add the menu header
			getChildren().add(menuIcon);
			
			// history visualizer
			historyVisualizer = new Rectangle();
			historyVisualizer.setMouseTransparent(true);
			historyVisualizer.xProperty().set(0);
			historyVisualizer.yProperty().set(0);
			historyVisualizer.widthProperty().bind(prefWidthProperty());
			historyVisualizer.heightProperty().bind(prefHeightProperty());
			getChildren().add(historyVisualizer);
			historyVisualizer.setVisible(false);
			historyVisualizer.getStyleClass().add("History");
		}
		final Rectangle historyVisualizer;
	}
	
	/**
	 * Responsible for rendering the days
	 */
	class WeekPane extends Pane
	{
		/**
		 * 
		 */
		public WeekPane()
		{
			getStyleClass().add("WeekPane");
			
			// draw times
			for (int lHour = 0; lHour < 24; lHour++)
			{
				// hour line
				{
					Line l = new Line(0,10,100,10);
					l.getStyleClass().add("HourLine");
					l.endXProperty().bind(widthProperty());
					l.endYProperty().bind(l.startYProperty());
					getChildren().add(l);
					hourLines.add(l);
				}
				// half hour line
				{
					Line l = new Line(0,10,100,10);
					l.getStyleClass().add("HalfHourLine");
					l.endXProperty().bind(widthProperty());
					l.endYProperty().bind(l.startYProperty());
					getChildren().add(l);
					halfHourLines.add(l);
				}
				// hour text
				{
					Text t = new Text(lHour + ":00");
					t.setTranslateY(t.getBoundsInParent().getHeight()); // move it under the line
					t.getStyleClass().add("HourLabel");
					t.setFontSmoothingType(FontSmoothingType.LCD);
					getChildren().add(t);
					hourTexts.add(t);
				}
			}

			// 7 days per week
			for (int i = 0; i < 7; i++)
			{
				DayPane lDay = new DayPane();
				getChildren().add(lDay);
				dayPanes.add(lDay);
			}
			
			// change the layout related to the size
			widthProperty().addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					relayout();
				}
			});
			heightProperty().addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					relayout();
				}
			});
			
			// layout
			relayout();
		}
		final List<DayPane> dayPanes = new ArrayList<DayPane>();
		final List<Text> hourTexts = new ArrayList<Text>();
		final List<Line> hourLines = new ArrayList<Line>();
		final List<Line> halfHourLines = new ArrayList<Line>();
		
		/**
		 * 
		 */
		private void relayout()
		{
			// position the hours
			for (int lHour = 0; lHour < 24; lHour++)
			{
				hourLines.get(lHour).startYProperty().set(lHour * hourHeight);
				halfHourLines.get(lHour).startXProperty().set(timeWidth);
				halfHourLines.get(lHour).startYProperty().set((lHour + 0.5) * hourHeight);
				hourTexts.get(lHour).xProperty().set(timeWidth - hourTexts.get(lHour).getBoundsInParent().getWidth());
				hourTexts.get(lHour).yProperty().set(lHour * hourHeight);
			}

			// position the day panes
			for (int i = 0; i < 7; i++)
			{
				DayPane lDay = dayPanes.get(i);
				lDay.setLayoutX(dayFirstColumnX + (i * dayWidth));
				lDay.setLayoutY(0.0);
				lDay.setPrefSize(dayWidth, dayHeight);
			}
		}
	}
	
	
	/**
	 * Responsible for rendering the appointments within a day 
	 */
	class DayPane extends Pane
	{
		// know your header
		DayHeaderPane dayHeader = null;
		
		public DayPane()
		{
			// for debugging setStyle("-fx-border-color:PINK;-fx-border-width:4px;");		
			getStyleClass().add("Day");
			
			// change the layout related to the size
			widthProperty().addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					relayout();
				}
			});
			heightProperty().addListener(new InvalidationListener()
			{
				@Override
				public void invalidated(Observable arg0)
				{
					relayout();
				}
			});
			
			// start new apppointment
			setOnMousePressed(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					// if there is no one to handle the result, don't eve bother
					if (getSkinnable().createAppointmentCallbackProperty().get() == null) return;
					
					// show the rectangle
					DayPane.this.setCursor(Cursor.V_RESIZE);
					double lY = mouseEvent.getScreenY() - NodeUtil.screenY(DayPane.this);
					resizeRectangle = new Rectangle(0, lY, dayWidth, 10);
					resizeRectangle.getStyleClass().add("ResizeRectangle");
					DayPane.this.getChildren().add(resizeRectangle);
					
					// this event should not be processed by the appointment area
					mouseEvent.consume();
					dragged = false;
					getSkinnable().selectedAppointments().clear();
				}
			});
			// visualize resize
			setOnMouseDragged(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (resizeRectangle == null) return;
					
					// - calculate the number of pixels from onscreen nodeY (layoutY) to onscreen mouseY					
					double lHeight = mouseEvent.getScreenY() - NodeUtil.screenY(resizeRectangle);
					if (lHeight < 5) lHeight = 5;
					resizeRectangle.setHeight(lHeight);
					
					// no one else
					mouseEvent.consume();
					dragged = true;
				}
			});
			// end resize
			setOnMouseReleased(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (resizeRectangle == null) return;
					
					// no one else
					mouseEvent.consume();
					
					// reset ui
					DayPane.this.setCursor(Cursor.HAND);
					DayPane.this.getChildren().remove(resizeRectangle);
					
					// must have dragged
					if (dragged == false) return;
					
					// calculate the starttime
					Calendar lStartCalendar = setTimeTo0000((Calendar)DayPane.this.calendarObjectProperty.get().clone());
					lStartCalendar.add(Calendar.MILLISECOND, (int)(resizeRectangle.getY() * durationInMSPerPixel));
					setTimeToNearestMinutes(lStartCalendar, 5);
					
					// calculate the new end date for the appointment (recalculating the duration)
					Calendar lEndCalendar = (Calendar)lStartCalendar.clone();					
					lEndCalendar.add(Calendar.MILLISECOND, (int)(resizeRectangle.getHeight() * durationInMSPerPixel));
					setTimeToNearestMinutes(lEndCalendar, 5);
					
					// ask the control to create a new appointment (null may be returned)
					Agenda.Appointment lAppointment = getSkinnable().createAppointmentCallbackProperty().get().call(new Agenda.CalendarRange(lStartCalendar, lEndCalendar));
					if (lAppointment != null) getSkinnable().appointments().add(lAppointment); // the appointments are listened to, so they will automatically be refreshed
					
					// clean up
					resizeRectangle = null;					
				}
			});
		}
		ObjectProperty<Calendar> calendarObjectProperty = new SimpleObjectProperty<Calendar>(DayPane.this, "calendar");
		Rectangle resizeRectangle = null;
		boolean dragged = false;
		
		/**
		 * 
		 * @return
		 */
		public List<AbstractAppointmentPane> collectAllPanes()
		{
			List<AbstractAppointmentPane> lPanes = new ArrayList<AgendaWeekSkin.AbstractAppointmentPane>(appointmentPanes);
			lPanes.addAll(wholedayAppointmentPanes);
			lPanes.addAll(dayHeader.appointmentHeaderPanes);
			return lPanes;
		}
		
		/**
		 * 
		 */
		private void relayout()
		{
			// first add all the whole day appointments
			int lWholedayCnt = 0;
			for (AppointmentPane lAppointmentPane : wholedayAppointmentPanes)
			{
				lAppointmentPane.setLayoutX(lWholedayCnt * wholedayAppointmentWidth);
				lAppointmentPane.setLayoutY(0);
				lAppointmentPane.setPrefSize(wholedayAppointmentWidth, dayHeight);
				lWholedayCnt++;
			}
			
			// then add all appointments to the day
			double lAppointmentsWidth = dayContentWidth - (lWholedayCnt * wholedayAppointmentWidth);			
			for (AppointmentPane lAppointmentPane : appointmentPanes)
			{
				int lOffsetY = (lAppointmentPane.start.get(Calendar.HOUR_OF_DAY) * 60) + lAppointmentPane.start.get(Calendar.MINUTE);
				lAppointmentPane.setLayoutX((lWholedayCnt * wholedayAppointmentWidth) + (lAppointmentsWidth / lAppointmentPane.clusterOwner.clusterTracks.size() * lAppointmentPane.clusterTrackIdx));
				lAppointmentPane.setLayoutY(dayHeight / (24 * 60) * lOffsetY );
				double lW = (dayContentWidth - (wholedayAppointmentPanes.size() * wholedayAppointmentWidth)) * (1.0 / (((double)lAppointmentPane.clusterOwner.clusterTracks.size())));
				if (lAppointmentPane.clusterTrackIdx < lAppointmentPane.clusterOwner.clusterTracks.size() - 1) lW *= 1.5;
				double lH = (dayHeight / (24 * 60) * (lAppointmentPane.durationInMS / 1000 / 60) );
				if (lH < 2 * padding) lH = 2 * padding; 
				lAppointmentPane.setPrefSize(lW, lH);
			}
		}			

		/**
		 * This method prepares a day for being drawn.
		 * The appointments within one day might overlap, this method will create a data structure so it is clear how these overlapping appointments should be drawn.
		 * All appointments in one day are process based on their start time; earliest first, and if there are more with the same start time, longest duration first.
		 * The appointments are then place onto (parallel) tracks; an appointment initially is placed in track 1. 
		 * But if there is already an (partially overlapping) appointment there, then the appointment is placed in track 2. 
		 * Unless there also is an appointment already in track 2, then track 3 is tried, then track 4, track 5, until a free slot is found.
		 * For example (the letters are not the sequence in which the appointments are processed, they're just for identifying them):
		 * 
		 *  tracks
		 *  1 2 3 4
		 *  -------
		 *  . . . .
		 *  . . . .
		 *  A . . .
		 *  A B C .
		 *  A B C D
		 *  A B . D
		 *  A . . D
		 *  A E . D
		 *  A . . D
		 *  . . . D
		 *  . . . D
		 *  F . . D
		 *  F H . D 
		 *  . . . .
		 *  G . . . 
		 *  . . . .
		 * 
		 * Appointment A was rendered first and put into track 1 and its start time.
		 * Then appointment B was added, initially it was put in track 1, but appointment A already uses the same timeslot, so B was moved into track 2.
		 * C moved from track 1, conflicting with A, to track 2, conflicting with B, and ended up in track 3.
		 * And so forth.
		 * F and H show that even though D overlaps them, they could perfectly be placed in lower tracks.
		 * 
		 * A cluster of appointments always starts with a free standing appointment in track 1, for example A or G, this appointment is called the cluster owner.
		 * When the next appointment is added to the tracks, and finds that it cannot be put in track 1, it will be added as a member to the cluster denoted by the appointment in track 1.
		 * The word "denoted" is used on purpose, because special attention must be paid to an appointment that is placed in track 1, but is linked to a cluster by a earlier appointment in a higher track.
		 * In the example above, F is linked by D to the cluster owned by A. So F is not a cluster of its own, but a member of the cluster owned by A.
		 * And appointment H through F is also part of the cluster owned by A.  
		 * G finally starts a new cluster.
		 * The cluster owner knows all members and how many tracks there are, each member has a direct link to the cluster owner. 
		 *   
		 * When rendering the appointments above, parallel appointments are rendered narrower & indented, so appointments partially overlap and a piece of all appointments is always visible to the user.
		 * In the example above the single appointment G is rendered full width, while for example A, B, C and D are overlapping.
		 * F and H are drawn in the same dimensions as A and B in order to allow D to overlap then.
		 * The size and amount of indentation depends on the number of appointments that are rendered next to each other.
		 * In order to compute its location and size, each appointment needs to know:
		 * - its start and ending time,
		 * - its track number,
		 * - its total number of tracks,
		 * - and naturally the total width and height available to draw the day.
		 * 
		 */
		public void setupAppointments()
		{
			// clear
			getChildren().removeAll(appointmentPanes);
			appointmentPanes.clear();
			getChildren().removeAll(wholedayAppointmentPanes);
			wholedayAppointmentPanes.clear();			
			if (calendarObjectProperty.get() == null) return;
			
			// scan all appointments and filter the ones for this day
			for (Agenda.Appointment lAppointment : getSkinnable().appointments())
			{				
				// check if the appointment falls in today
				// appointments may span multiple days, but the appointment pane will clamp the start and end date
				AppointmentPane lAppointmentPane = new AppointmentPane(calendarObjectProperty.get(), lAppointment);
				lAppointmentPane.dayPane = this;
				if ( isSameDay(calendarObjectProperty.get(), lAppointmentPane.start) 
				  && isSameDay(calendarObjectProperty.get(), lAppointmentPane.end)
				   )
				{
					if (lAppointment.isWholeDay())
					{
						wholedayAppointmentPanes.add(lAppointmentPane);
					}
					else
					{
						appointmentPanes.add(lAppointmentPane);
					}
				}
			}
			
			// sort on start time and then decreasing duration
			Collections.sort(appointmentPanes, new Comparator<AppointmentPane>()
			{
				@Override
				public int compare(AppointmentPane o1, AppointmentPane o2)
				{
					if (o1.startAsString.equals(o2.startAsString) == false)
					{
						return o1.startAsString.compareTo(o2.startAsString);
					}
					return o1.durationInMS > o2.durationInMS ? -1 : 1;
				}
			});
			
			// start placing appointments in the tracks
			AppointmentPane lClusterOwner = null;
			for (AppointmentPane lAppointmentPane : appointmentPanes)
			{
				// if there is no cluster owner
				if (lClusterOwner == null)
				{
					// than the current becomes an owner
					// only create a minimal cluster, because it will be setup fully in the code below
					lClusterOwner = lAppointmentPane;
					lClusterOwner.clusterTracks = new ArrayList<List<AppointmentPane>>();
				}
				
				// in which track should it be added
				int lTrackNr = determineTrackWhereAppointmentCanBeAdded(lClusterOwner.clusterTracks, lAppointmentPane);
				// if it can be added to track 0, then we have a "situation". Track 0 could mean 
				// - we must start a new cluster
				// - the appointment is still linked to the running cluster by means of a linking appointment in the higher tracks
				if (lTrackNr == 0)
				{
					// So let's see if there is a linking appointment higher up
					boolean lOverlaps = false;
					for (int i = 1; i < lClusterOwner.clusterTracks.size() && lOverlaps == false; i++)
					{
						lOverlaps = checkIfTheAppointmentOverlapsAnAppointmentAlreadyInThisTrack(lClusterOwner.clusterTracks, i, lAppointmentPane);
					}
					
					// if it does not overlap, we start a new cluster
					if (lOverlaps == false)
					{
						lClusterOwner = lAppointmentPane;
						lClusterOwner.clusterMembers = new ArrayList<AppointmentPane>(); 
						lClusterOwner.clusterTracks = new ArrayList<List<AppointmentPane>>();
						lClusterOwner.clusterTracks.add(new ArrayList<AppointmentPane>());
					}
				}
				
				// add it to the track (and setup all other cluster data)
				lClusterOwner.clusterMembers.add(lAppointmentPane);
				lClusterOwner.clusterTracks.get(lTrackNr).add(lAppointmentPane);
				lAppointmentPane.clusterOwner = lClusterOwner;
				lAppointmentPane.clusterTrackIdx = lTrackNr;				
				// for debug  System.out.println("----"); for (int i = 0; i < lClusterOwner.clusterTracks.size(); i++) { System.out.println(i + ": " + lClusterOwner.clusterTracks.get(i) ); } System.out.println("----");
			}
			
			// and layout
			getChildren().addAll(wholedayAppointmentPanes);
			getChildren().addAll(appointmentPanes);
			relayout();
			
			// we're done, now have the header updated
			dayHeader.setupAppointments();
		}
		final List<AppointmentPane> appointmentPanes = new ArrayList<AppointmentPane>(); // all appointments that need to be drawn
		final List<AppointmentPane> wholedayAppointmentPanes = new ArrayList<AppointmentPane>(); // all appointments that need to be drawn
	}
	
	/**
	 * Responsible for rendering a single appointment on a single day.
	 * An appointment region is a representation of an appointment in one single day.
	 * Appointments may span multiple days, each day gets its own appointment region.
	 * 
	 */
	class AppointmentPane extends AbstractAppointmentPane
	{
		DayPane dayPane = null;
		// for the role of cluster owner
		List<AppointmentPane> clusterMembers = null; 
		List<List<AppointmentPane>> clusterTracks = null;
		// for the role of cluster member
		AppointmentPane clusterOwner = null;
		int clusterTrackIdx = -1;

		/**
		 * 
		 * @param calendar
		 * @param appointment
		 */
		public AppointmentPane(Calendar calendar, Agenda.Appointment appointment)
		{
			super(appointment);
			
			// for debugging setStyle("-fx-border-color:BLUE;-fx-border-width:4px;");
			getStyleClass().add("Appointment");
			getStyleClass().add(appointment.getAppointmentGroup().getStyleClass());
			
			// wholeday
			if (appointment.isWholeDay())
			{
				// start
				this.start = setTimeTo0000( (Calendar)appointment.getStartTime().clone() );
				
				// end
				this.end = setTimeTo2359( (Calendar)appointment.getStartTime().clone() );
			}
			else
			{
				// start
				Calendar lDayStartCalendar = setTimeTo0000( (Calendar)calendar.clone() );
				this.start = (appointment.getStartTime().before(lDayStartCalendar) ? lDayStartCalendar : (Calendar)appointment.getStartTime().clone());
				
				// end
				Calendar lDayEndCalendar = setTimeTo2359( (Calendar)calendar.clone() );
				this.end = (appointment.getEndTime().after(lDayEndCalendar) ? lDayEndCalendar : (Calendar)appointment.getEndTime().clone());
				
				// always is first and last appointment
				isFirstAreaOfAppointment = this.start.equals(appointment.getStartTime()); 
				isLastAreaOfAppointment = this.end.equals(appointment.getEndTime()); 
			}
		
			// duration
			durationInMS = this.end.getTimeInMillis() - this.start.getTimeInMillis();
			
			// strings
			this.startAsString = iTimeFormat.format(this.start.getTime());
			this.endAsString = iTimeFormat.format(this.end.getTime());
			
			// only for whole day events
			if (appointment.isWholeDay() == false)
			{
				// add the duration as text
				Text lTimeText = new Text(startAsString + "-" + endAsString);
				{
					lTimeText.getStyleClass().add("AppointmentTimeLabel");
					lTimeText.setX( padding );
					lTimeText.setY(lTimeText.prefHeight(0));
					Rectangle lClip = new Rectangle(0,0,0,0);
					lClip.widthProperty().bind(widthProperty().subtract(padding));
					lClip.heightProperty().bind(heightProperty());
					lTimeText.setClip(lClip);
					getChildren().add(lTimeText);
				}
				// add summary
				Text lSummaryText = new Text(appointment.getSummary());
				{
					lSummaryText.getStyleClass().add("AppointmentLabel");
					lSummaryText.setX( padding );
					lSummaryText.setY( lTimeText.getY() + textHeight1M);
					lSummaryText.wrappingWidthProperty().bind(widthProperty().subtract(padding));
					Rectangle lClip = new Rectangle(0,0,0,0);
					lClip.widthProperty().bind(widthProperty());
					lClip.heightProperty().bind(heightProperty().subtract(padding));
					lSummaryText.setClip(lClip);
					getChildren().add(lSummaryText);			
				}
			}
			
			// duration dragger
			if (isLastAreaOfAppointment == false)
			{
				durationDragger = null;
			}
			else
			{
				durationDragger = new DurationDragger(this);
				durationDragger.xProperty().bind(widthProperty().multiply(0.25));
				durationDragger.yProperty().bind(heightProperty().subtract(5));
				durationDragger.widthProperty().bind(widthProperty().multiply(0.5));
				durationDragger.setHeight(3);
				getChildren().add(durationDragger);
			}
			
			// add the menu header
			if (appointment.isWholeDay() == false) getChildren().add(menuIcon);
			
			// history visualizer
			historyVisualizer = new Rectangle();
			historyVisualizer.setMouseTransparent(true);
			historyVisualizer.xProperty().set(0);
			historyVisualizer.yProperty().set(0);
			historyVisualizer.widthProperty().bind(prefWidthProperty());
			historyVisualizer.heightProperty().bind(prefHeightProperty());
			getChildren().add(historyVisualizer);
			historyVisualizer.setVisible(false);
			historyVisualizer.getStyleClass().add("History");
		}
		final Rectangle historyVisualizer;
		final Calendar start;
		final String startAsString;
		final Calendar end;
		final String endAsString;
		final long durationInMS;
		final DurationDragger durationDragger;
		
		/**
		 * 
		 */
		public String toString()
		{
			return super.toString()
				 + ";" + startAsString + "-" + endAsString
				 + ";" + durationInMS + "ms"
				 ;
		}
	}

			
	// ==================================================================================================================
	// RESIZING
	
	/**
	 * for the popup menu
	 */
	class MenuIcon extends Rectangle
	{
		public MenuIcon(final AbstractAppointmentPane abstractAppointmentPane)
		{
			setX(padding);
			setY(padding);
			setWidth(6);
			setHeight(3);
			getStyleClass().add("MenuIcon");
			// play with the mouse pointer to show something can be done here
			setOnMouseEntered(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{						
						MenuIcon.this.setCursor(Cursor.HAND);
						
						// no one else
						mouseEvent.consume();
					}
				}
			});
			setOnMouseExited(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{
						MenuIcon.this.setCursor(Cursor.DEFAULT);
						
						// no one else
						mouseEvent.consume();
					}
				}
			});
			setOnMousePressed(new EventHandler<MouseEvent>() // these just need to be captured, so they are not processed by the underlying pane
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					mouseEvent.consume();
				}
			});
			setOnMouseReleased(new EventHandler<MouseEvent>() // these just need to be captured, so they are not processed by the underlying pane
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					mouseEvent.consume();
				}
			});
			setOnMouseClicked(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					mouseEvent.consume();
					showMenu(mouseEvent, abstractAppointmentPane);
				}
			});
		}
	}
	
	// ==================================================================================================================
	// RESIZING
	
	/**
	 * 
	 */
	class DurationDragger extends Rectangle
	{
		public DurationDragger(AppointmentPane appointmentPane)
		{
			// remember
			this.appointmentPane = appointmentPane;
			
			// styling
			getStyleClass().add("DurationDragger");

			// play with the mouse pointer to show something can be done here
			setOnMouseEntered(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{						
						DurationDragger.this.setCursor(Cursor.HAND);
						
						// no one else
						mouseEvent.consume();
					}
				}
			});
			setOnMouseExited(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{
						DurationDragger.this.setCursor(Cursor.DEFAULT);
						
						// no one else
						mouseEvent.consume();
					}
				}
			});
			// start resize
			setOnMousePressed(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					// // record a delta distance for the drag and drop operation.
					// dragDelta.x = stage.getX() - mouseEvent.getScreenX();
					// dragDelta.y = stage.getY() - mouseEvent.getScreenY();
					DurationDragger.this.setCursor(Cursor.V_RESIZE);
					resizeRectangle = new Rectangle(DurationDragger.this.appointmentPane.getLayoutX(), DurationDragger.this.appointmentPane.getLayoutY(), DurationDragger.this.appointmentPane.getWidth(), DurationDragger.this.appointmentPane.getHeight());
					resizeRectangle.getStyleClass().add("ResizeRectangle");
					DurationDragger.this.appointmentPane.dayPane.getChildren().add(resizeRectangle);
					
					// this event should not be processed by the appointment area
					mouseEvent.consume();
				}
			});
			// visualize resize
			setOnMouseDragged(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					// - calculate the number of pixels from onscreen nodeY (layoutY) to onscreen mouseY					
					double lNodeScreenY = NodeUtil.screenY(DurationDragger.this.appointmentPane);
					double lMouseY = mouseEvent.getScreenY();
					double lHeight = lMouseY - lNodeScreenY;
					if (lHeight < 5) lHeight = 5;
					resizeRectangle.setHeight(lHeight);
					
					// no one else
					mouseEvent.consume();
				}
			});
			// end resize
			setOnMouseReleased(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{					
					// - calculate the new end date for the appointment (recalculating the duration)
					int ms = (int)(resizeRectangle.getHeight() * durationInMSPerPixel);
					Calendar lCalendar = (Calendar)DurationDragger.this.appointmentPane.appointment.getStartTime().clone();					
					lCalendar.add(Calendar.MILLISECOND, ms);
					
					// align to X minutes accuracy
					setTimeToNearestMinutes(lCalendar, 5);
					
					// set the new enddate
					DurationDragger.this.appointmentPane.appointment.setEndTime(lCalendar);
					
					// redo whole week
					setupAppointments();
									
					// reset ui
					DurationDragger.this.setCursor(Cursor.HAND);
					DurationDragger.this.appointmentPane.dayPane.getChildren().remove(resizeRectangle);
					resizeRectangle = null;					
					
					// no one else
					mouseEvent.consume();
				}
			});
		}
		final AppointmentPane appointmentPane;
		Rectangle resizeRectangle;
	}
	
	
	// ==================================================================================================================
	// GENERIC APPOINTMENT AREA
	
	/**
	 * This class handles shared logic like dragging and focusable. 
	 * TODO: (multi)select?
	 * TODO: shouldn't we be using JFX drag features?
	 */
	abstract class AbstractAppointmentPane extends Pane
	{
		Agenda.Appointment appointment = null;
		boolean isFirstAreaOfAppointment = true;
		boolean isLastAreaOfAppointment = true;

		/**
		 * 
		 */
		public AbstractAppointmentPane(Agenda.Appointment appointment)
		{
			// remember
			this.appointment = appointment;

			// tooltip
			Tooltip.install(this, new Tooltip(appointment.getSummary()));
			
			// start resize
			setOnMousePressed(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{					
					// no one else
					mouseEvent.consume();
					if (mouseEvent.isPrimaryButtonDown() == false) return;

					// no drag yet
					dragged = mouseEvent.isPrimaryButtonDown() ? false : true; // if not primary mouse, then just assume drag from the start 
					if (isFirstAreaOfAppointment == false) return; // TODO: temporarily

					// place the rectangle
					AbstractAppointmentPane.this.setCursor(Cursor.MOVE);
					double lX = NodeUtil.screenX(AbstractAppointmentPane.this) - NodeUtil.screenX(AgendaWeekSkin.this);
					double lY = NodeUtil.screenY(AbstractAppointmentPane.this) - NodeUtil.screenY(AgendaWeekSkin.this);
					dragRectangle = new Rectangle(lX, lY, AbstractAppointmentPane.this.getWidth(), (AbstractAppointmentPane.this.appointment.isWholeDay() ? titleCalendarHeight : AbstractAppointmentPane.this.getHeight()) );
					dragRectangle.getStyleClass().add("ResizeRectangle");
					dragPane.getChildren().add(dragRectangle);
					
					// remember
					startX = mouseEvent.getScreenX();
					startY = mouseEvent.getScreenY();
				}
			});
			// visualize resize
			setOnMouseDragged(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					// no one else
					mouseEvent.consume();
					if (mouseEvent.isPrimaryButtonDown() == false) return;

					// no dragged
					dragged = true;
					if (dragRectangle == null) return;
					
					double lDeltaX = mouseEvent.getScreenX() - startX;
					double lDeltaY = mouseEvent.getScreenY() - startY;
					double lX = NodeUtil.screenX(AbstractAppointmentPane.this) - NodeUtil.screenX(AgendaWeekSkin.this) + lDeltaX;
					double lY = NodeUtil.screenY(AbstractAppointmentPane.this) - NodeUtil.screenY(AgendaWeekSkin.this) + lDeltaY;
					dragRectangle.setX(lX);
					dragRectangle.setY(lY);
					
					// no one else
					mouseEvent.consume();
				}
			});
			// end resize
			setOnMouseReleased(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					// no one else
					mouseEvent.consume();

					// reset ui
					boolean lDragged = (dragRectangle != null);
					AbstractAppointmentPane.this.setCursor(Cursor.HAND);
					dragPane.getChildren().remove(dragRectangle);
					dragRectangle = null;					
					
					// if not dragged, then we're selecting
					if (dragged == false)
					{
						// if not shift pressed, clear the selection
						if (mouseEvent.isShiftDown() == false)
						{
							getSkinnable().selectedAppointments().clear();
						}
						// add to selection if not already added
						if (getSkinnable().selectedAppointments().contains(AbstractAppointmentPane.this.appointment) == false)
						{
							getSkinnable().selectedAppointments().add(AbstractAppointmentPane.this.appointment);
						}
						return;
					}
					
					// ------------
					// dragging
					
					if (lDragged == false) return;
					
					// find out where it was dropped
					for (DayPane lDayPane : weekPane.dayPanes)
					{
						double lDayX = NodeUtil.screenX(lDayPane); 
						double lDayY = NodeUtil.screenY(lDayPane); 
						if ( lDayX <= mouseEvent.getScreenX() && mouseEvent.getScreenX() < lDayX + lDayPane.getWidth()
						  && lDayY <= mouseEvent.getScreenY() && mouseEvent.getScreenY() < lDayY + lDayPane.getHeight()
						   )
						{
							// get the appointment that needs handling
							Appointment lAppointment = AbstractAppointmentPane.this.appointment;
							Calendar lDroppedOnCalendar = lDayPane.calendarObjectProperty.get();
		
							// is wholeday now, will become partial
							if (lAppointment.isWholeDay())
							{
								// calculate new start
								Calendar lStartCalendar = copyYMD( lDroppedOnCalendar, (Calendar)lAppointment.getStartTime().clone() );
								// and end times
								Calendar lEndCalendar = lAppointment.getEndTime() == null ? setTimeTo2359( (Calendar)lDroppedOnCalendar.clone() ) : copyYMD( lDroppedOnCalendar, (Calendar)lAppointment.getEndTime().clone() );
								
								// set the new enddate
								lAppointment.setStartTime(lStartCalendar);
								lAppointment.setEndTime(lEndCalendar);
								
								// no longer whole day
								lAppointment.setWholeDay(false);
							}
							else
							{
								// duration
								long lDurationInMS = lAppointment.getEndTime().getTimeInMillis() - lAppointment.getStartTime().getTimeInMillis();
								
								// calculate new start
								Calendar lStartCalendar = copyYMD(lDroppedOnCalendar, (Calendar)lAppointment.getStartTime().clone());
	
								// also add the delta Y minutes
								int lDeltaDurationInMS = (int)((mouseEvent.getScreenY() - startY) * durationInMSPerPixel);
								lStartCalendar.add(Calendar.MILLISECOND, lDeltaDurationInMS);
								setTimeToNearestMinutes(lStartCalendar, 5);
								while (isSameDay(lStartCalendar, lDroppedOnCalendar) == false && lStartCalendar.before(lDroppedOnCalendar)) { lStartCalendar.add(Calendar.MINUTE, 1);  }// the delta may have pushed it out of today 
								while (isSameDay(lStartCalendar, lDroppedOnCalendar) == false && lStartCalendar.after(lDroppedOnCalendar)) { lStartCalendar.add(Calendar.MINUTE, -1);  }// the delta may have pushed it out of today
								
								// calculate
								Calendar lEndCalendar = (Calendar)lStartCalendar.clone();
								lEndCalendar.add(Calendar.MILLISECOND, (int)lDurationInMS);
								
								// set the new enddate
								lAppointment.setStartTime(lStartCalendar);
								lAppointment.setEndTime(lEndCalendar);
							}
						}
					}
					
					// find out where it was dropped
					for (DayHeaderPane lDayHeaderPane : weekHeaderPane.dayHeaderPanes)
					{
						double lDayX = NodeUtil.screenX(lDayHeaderPane); 
						double lDayY = NodeUtil.screenY(lDayHeaderPane); 
						if ( lDayX <= mouseEvent.getScreenX() && mouseEvent.getScreenX() < lDayX + lDayHeaderPane.getWidth()
						  && lDayY <= mouseEvent.getScreenY() && mouseEvent.getScreenY() < lDayY + lDayHeaderPane.getHeight()
						   )
						{
							// get the appointment that needs handling
							Appointment lAppointment = AbstractAppointmentPane.this.appointment;
							
							// calculate new start
							Calendar lStartCalendar = copyYMD(lDayHeaderPane.dayPane.calendarObjectProperty.get(), (Calendar)lAppointment.getStartTime().clone() );
							
							// set the new start date
							lAppointment.setStartTime(lStartCalendar);
							
							// enddate can be ignored
							
							// now a whole day (just in case it was)
							lAppointment.setWholeDay(true);
						}
					}
					
					// redo whole week
					setupAppointments();					
				}
			});
			setOnMouseClicked(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					mouseEvent.consume();
					if (mouseEvent.isSecondaryButtonDown())
					{
						showMenu(mouseEvent, AbstractAppointmentPane.this);
					}
				}
			});
			
			// setup menu arrow
			menuIcon = new MenuIcon(this);
		}
		Rectangle dragRectangle;
		double startX = 0;
		double startY = 0;
		boolean dragged = false;
		Rectangle menuIcon = null;
		
		public String describe()
		{
			// strings
			StringBuilder lStringBuilder = new StringBuilder();
			lStringBuilder.append( iDateFormat.format(this.appointment.getStartTime().getTime()) );
			if (this.appointment.isWholeDay() == false)
			{
				lStringBuilder.append( " " );
				lStringBuilder.append( iTimeFormat.format(this.appointment.getStartTime().getTime()) );
				lStringBuilder.append( " - " );
				if (isSameDay(this.appointment.getStartTime(), this.appointment.getEndTime()) == false)
				{
					lStringBuilder.append( iDateFormat.format(this.appointment.getEndTime().getTime()) );
					lStringBuilder.append( " " );
				}
				lStringBuilder.append( iTimeFormat.format(this.appointment.getEndTime().getTime()) );
			}
			return lStringBuilder.toString();
		}
	}
	AbstractAppointmentPane focused = null;

	
	// ==================================================================================================================
	// NOW
	
	final Rectangle nowLine = new Rectangle(0,0,0,0);
	Runnable nowUpdateRunnable = new Runnable()
	{
		{
			nowLine.getStyleClass().add("Now");
		}
		
		@Override
		public void run()
		{
			//  get now
			Calendar lNow = Calendar.getInstance();
			
			// see if we are displaying now
			// check all days
			boolean lFound = false;
			for (DayPane lDayPane : weekPane.dayPanes)
			{
				// if the calendar of the day is the same day as now
				if (isSameDay(lDayPane.calendarObjectProperty.get(), lNow))
				{
					lFound = true;
					
					// add if not present
					if (weekPane.getChildren().contains(nowLine) == false)
					{
						weekPane.getChildren().add(nowLine);
					}

					// place it
					int lOffsetY = (lNow.get(Calendar.HOUR_OF_DAY) * 60) + lNow.get(Calendar.MINUTE);
					nowLine.setX(lDayPane.getLayoutX());
					nowLine.setY(dayHeight / (24 * 60) * lOffsetY );
					nowLine.setHeight(3);
					nowLine.setWidth(dayWidth);	
				}
				
				// display history
				for (AppointmentPane lAppointmentPane : lDayPane.appointmentPanes)
				{
					lAppointmentPane.historyVisualizer.setVisible( lAppointmentPane.start.before(lNow));
				}
				for (AppointmentPane lAppointmentPane : lDayPane.wholedayAppointmentPanes)
				{
					lAppointmentPane.historyVisualizer.setVisible( lAppointmentPane.start.before(lNow));
				}
			}
			
			// if cannot be placed, remove
			if (lFound == false)
			{
				weekPane.getChildren().remove(nowLine);
			}
			
			// also for headers
			for (DayHeaderPane lDayHeaderPane : weekHeaderPane.dayHeaderPanes)
			{
				for (AppointmentHeaderPane lAppointmentHeaderPane : lDayHeaderPane.appointmentHeaderPanes)
				{
					lAppointmentHeaderPane.historyVisualizer.setVisible(lAppointmentHeaderPane.appointment.getStartTime().before(lNow));
				}
			}
		}
	};
	
	/**
	 * This timer takes care of visualizing NOW
	 */
	Timer nowTimer = new Timer(nowUpdateRunnable)
		.withCycleDuration(new Duration(60 * 1000))// every minute
		.withDelay(new Duration( (60 - Calendar.getInstance().get(Calendar.SECOND)) * 1000)) // start on the minute
		.start();  
	
	// ==================================================================================================================
	// POPUP

	/*
	 * 
	 */
	private void showMenu(MouseEvent evt, final AbstractAppointmentPane abstractAppointmentPane)
	{
		// TODO: should we use an intermediate appointment and have an ok button which copies all data?
		
		// create popup
		final Popup lPopup = new Popup();
		lPopup.setAutoFix(true);
		lPopup.setAutoHide(true);
		lPopup.setHideOnEscape(true);
		lPopup.setOnHidden(new EventHandler<WindowEvent>()
		{
			@Override
			public void handle(WindowEvent arg0)
			{
				setupAppointments();
			}
		});

		BorderPane lBorderPane = new BorderPane();
		lBorderPane.getStyleClass().add(this.getClass().getSimpleName() + "_popup");
		lPopup.getContent().add(lBorderPane);
		
		// close icon
		ImageView lImageView = new ImageView(closeIconImage);
		lImageView.setPickOnBounds(true);
		lImageView.setOnMouseClicked(new EventHandler<MouseEvent>()
		{
			@Override public void handle(MouseEvent evt)
			{
				lPopup.hide(); 
			}
		});
		lBorderPane.setRight(lImageView);

		// initial layout
		VBox lMenuVBox = new VBox(padding);
		lBorderPane.setCenter(lMenuVBox);

		// time
		lMenuVBox.getChildren().add(new Text("Time:"));
		// start
		final CalendarTextField lStartCalendarTextField = new CalendarTextField().withShowTime(true);
		lStartCalendarTextField.setLocale(getSkinnable().getLocale());
		lStartCalendarTextField.setValue(abstractAppointmentPane.appointment.getStartTime());
		lMenuVBox.getChildren().add(lStartCalendarTextField);
		// end
		final CalendarTextField lEndCalendarTextField = new CalendarTextField().withShowTime(true);
		lEndCalendarTextField.setLocale(getSkinnable().getLocale());
		lEndCalendarTextField.setValue(abstractAppointmentPane.appointment.getEndTime());
		lMenuVBox.getChildren().add(lEndCalendarTextField);
		lEndCalendarTextField.valueProperty().addListener(new ChangeListener<Calendar>()
		{
			@Override
			public void changed(ObservableValue<? extends Calendar> arg0, Calendar oldValue, Calendar newValue)
			{
				abstractAppointmentPane.appointment.setEndTime(newValue);
				// refresh is done upon popup close
			}
		});
		lEndCalendarTextField.setVisible(abstractAppointmentPane.appointment.getEndTime() != null);
		// wholeday
		final CheckBox lWholedayCheckBox = new CheckBox("Wholeday");
		lWholedayCheckBox.selectedProperty().set(abstractAppointmentPane.appointment.isWholeDay());
		lMenuVBox.getChildren().add(lWholedayCheckBox);
		lWholedayCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>()
		{
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue)
			{
				abstractAppointmentPane.appointment.setWholeDay(newValue);
				if (newValue == true) 
				{
					abstractAppointmentPane.appointment.setEndTime(null);
				}
				else
				{
					Calendar lEndTime = (Calendar)abstractAppointmentPane.appointment.getStartTime().clone();
					lEndTime.add(Calendar.MINUTE, 30);
					abstractAppointmentPane.appointment.setEndTime(lEndTime);
					lEndCalendarTextField.setValue(abstractAppointmentPane.appointment.getEndTime());
				}
				lEndCalendarTextField.setVisible(abstractAppointmentPane.appointment.getEndTime() != null);
				// refresh is done upon popup close
			}
		});
		// event handling
		lStartCalendarTextField.valueProperty().addListener(new ChangeListener<Calendar>()
		{
			@Override
			public void changed(ObservableValue<? extends Calendar> arg0, Calendar oldValue, Calendar newValue)
			{
				// enddate
				if (abstractAppointmentPane.appointment.isWholeDay())
				{
					abstractAppointmentPane.appointment.setStartTime(newValue);
				}
				else
				{
					// calculate duration
					long lDurationInMS = abstractAppointmentPane.appointment.getEndTime().getTimeInMillis() - abstractAppointmentPane.appointment.getStartTime().getTimeInMillis();
					
					// set
					abstractAppointmentPane.appointment.setStartTime(newValue);
					
					// end date
					Calendar lEndCalendar = (Calendar)abstractAppointmentPane.appointment.getStartTime().clone();
					lEndCalendar.add(Calendar.MILLISECOND, (int)lDurationInMS);
					abstractAppointmentPane.appointment.setEndTime(lEndCalendar);
					
					// update field
					lEndCalendarTextField.setValue(abstractAppointmentPane.appointment.getEndTime());
					
					// refresh is done upon popup close
				}
			}
		});
		
		// summary
		lMenuVBox.getChildren().add(new Text("Summary:"));
		TextField lSummaryTextField = new TextField();
		lSummaryTextField.setText(abstractAppointmentPane.appointment.getSummary());
		lSummaryTextField.textProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> arg0, String oldValue, String newValue)
			{
				abstractAppointmentPane.appointment.setSummary(newValue);
				// refresh is done upon popup close
			}
		});
		lMenuVBox.getChildren().add(lSummaryTextField);
		
		// location
		lMenuVBox.getChildren().add(new Text("Location:"));
		TextField lLocationTextField = new TextField();
		lLocationTextField.setText( abstractAppointmentPane.appointment.getLocation() == null ? "" : abstractAppointmentPane.appointment.getLocation());
		lLocationTextField.textProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> arg0, String oldValue, String newValue)
			{
				abstractAppointmentPane.appointment.setLocation(newValue);
				// refresh is done upon popup close
			}
		});
		lMenuVBox.getChildren().add(lLocationTextField);
		
		// actions
		VBox lActionsVBox = new VBox();
		lActionsVBox.getChildren().add(new Text("Actions:"));
		HBox lHBox = new HBox();
		lActionsVBox.getChildren().add(lHBox);
		// delete
		{
			ImageButton lImageButton = new ImageButton( new Image(this.getClass().getResourceAsStream("jqueryMobileBlack16x16/delete.png")) );
			lImageButton.setOnMouseClicked(new EventHandler<MouseEvent>()
			{
				@Override public void handle(MouseEvent evt)
				{
					lPopup.hide();
					getSkinnable().selectedAppointments().remove(abstractAppointmentPane.appointment); // TODO: we should make sure this happens in the control when the appointment is remove from the appointments collection
					getSkinnable().appointments().remove(abstractAppointmentPane.appointment);
					// refresh is done via the collection events
				}
			});
			Tooltip.install(lImageButton, new Tooltip("Delete"));
			lHBox.getChildren().add(lImageButton);
		}
		lMenuVBox.getChildren().add(lActionsVBox);
		
		// construct a area of appointment groups
		VBox lAppointmentGroupVBox = new VBox();
		lAppointmentGroupVBox.getChildren().add(new Text("Group:"));
		GridPane lAppointmentGroupGridPane = new GridPane();
		lAppointmentGroupVBox.getChildren().add(lAppointmentGroupGridPane);
		lAppointmentGroupGridPane.getStyleClass().add("AppointmentGroups");
		lAppointmentGroupGridPane.setHgap(2);
		lAppointmentGroupGridPane.setVgap(2);
		int lCnt = 0;
		for (Agenda.AppointmentGroup lAppointmentGroup : getSkinnable().appointmentGroups())
		{
			// create the appointment group
			final Pane lPane = new Pane();			
			lPane.setPrefSize(15, 15);
			lPane.getStyleClass().addAll("AppointmentGroup", lAppointmentGroup.getStyleClass());
			lAppointmentGroupGridPane.add(lPane, lCnt % 10, lCnt / 10 );
			lCnt++;
			
			// tooltip
			Tooltip.install(lPane, new Tooltip(lAppointmentGroup.getDescription()));
			 
			// mouse reactions
			lPane.setOnMouseEntered(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{						
						mouseEvent.consume();
						lPane.setCursor(Cursor.HAND);
					}
				}
			});
			lPane.setOnMouseExited(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{
						mouseEvent.consume();
						lPane.setCursor(Cursor.DEFAULT);
					}
				}
			});
			final Agenda.AppointmentGroup lAppointmentGroupFinal = lAppointmentGroup; 
			lPane.setOnMouseClicked(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					mouseEvent.consume();
					lPopup.hide();
					
					// assign appointment group
					abstractAppointmentPane.appointment.setAppointmentGroup(lAppointmentGroupFinal);
					
					// refresh is done upon popup close
				}
			});
		}
		lMenuVBox.getChildren().add(lAppointmentGroupVBox);
		
		// show it just below the menu icon
		lPopup.show(abstractAppointmentPane, NodeUtil.screenX(abstractAppointmentPane), NodeUtil.screenY(abstractAppointmentPane.menuIcon) + abstractAppointmentPane.menuIcon.getHeight());
	}

	// ==================================================================================================================
	// SUPPORT

	/**
	 * 
	 */
	private void calculateSizes()
	{
		// generic
		double scrollbarSize = 15; // TODO: derive this from an actual ScrollBar.
		textHeight1M = new Text("X").getBoundsInParent().getHeight();
		
		// header
		maxNumberOfWholedayAppointments = 0;
		for (DayPane lDay : weekPane.dayPanes)
		{
			if (lDay.wholedayAppointmentPanes.size() > maxNumberOfWholedayAppointments)
			{
				maxNumberOfWholedayAppointments = lDay.wholedayAppointmentPanes.size();
			}
		}
		titleCalendarHeight = 1.5 * textHeight1M; 
		wholedayTitleHeight = textHeight1M + 5; // not sure why the 5 is needed
		headerHeight = titleCalendarHeight + (maxNumberOfWholedayAppointments * wholedayTitleHeight);

		// time column
		int lTimeColumnWhitespace = 10;
		timeWidth = new Text("88:88").getBoundsInParent().getWidth() + lTimeColumnWhitespace;
		
		// day columns
		dayFirstColumnX = timeWidth + lTimeColumnWhitespace;
		dayWidth = (getSkinnable().getWidth() - timeWidth - scrollbarSize) / 7; // 7 days per week
		if (weekScrollPane.viewportBoundsProperty().get() != null) 
		{
			dayWidth = (weekScrollPane.viewportBoundsProperty().get().getWidth() - timeWidth) / 7; // 7 days per week
		}
		dayContentWidth = dayWidth - 10;
		
		// hour height
		hourHeight = (2 * textHeight1M) + 10; // 10 is padding
		if (weekScrollPane.viewportBoundsProperty().get() != null && (weekScrollPane.viewportBoundsProperty().get().getHeight() - scrollbarSize) > hourHeight * 24)
		{
			// if there is more room than absolutely required, let the height grow with the available room
			hourHeight = (weekScrollPane.viewportBoundsProperty().get().getHeight() - scrollbarSize) / 24;
		}
		dayHeight = hourHeight * 24;
		durationInMSPerPixel = (24 * 60 * 60 * 1000) / dayHeight;
		
		// if the viewport is active
		if (weekScrollPane.viewportBoundsProperty() != null && weekScrollPane.viewportBoundsProperty().get() != null)
		{
			// make sure the border pane uses these sizes
			weekHeaderPane.setPrefSize(weekScrollPane.viewportBoundsProperty().get().getWidth(), headerHeight);
			weekPane.setPrefSize(weekScrollPane.viewportBoundsProperty().get().getWidth(), 24 * hourHeight);				
		}
	}
	double padding = 3;
	double textHeight1M = 0;
	double titleCalendarHeight = 0;
	double headerHeight = 0;
	int maxNumberOfWholedayAppointments = 0;
	double wholedayTitleHeight = 0;
	double wholedayAppointmentWidth = 5;
	double timeWidth = 0;
	double dayFirstColumnX = 0;
	double dayWidth = 0;
	double dayContentWidth = 0;
	double dayHeight = 0;
	double durationInMSPerPixel = 0;
	double hourHeight = 0;
	
	/**
	 * get the calendar for the first day of the week
	 */
	protected Calendar getFirstDayOfWeekCalendar()
	{
		// result
		Calendar lLocalCalendar = Calendar.getInstance(getSkinnable().getLocale());
		int lFirstDayOfWeek = lLocalCalendar.getFirstDayOfWeek();
		
		// now get the displayed calendar
		Calendar lDisplayedCalendar = getSkinnable().getDisplayedCalendar();
		
		// this is the first day of week calendar
		Calendar lFirstDayOfWeekCalendar = (Calendar)getSkinnable().getDisplayedCalendar().clone();
		
		// if not on the first day of the week, correct with the appropriate amount
		lFirstDayOfWeekCalendar.add(Calendar.DATE, lFirstDayOfWeek - lFirstDayOfWeekCalendar.get(Calendar.DAY_OF_WEEK));
		
		// make sure we are in the same week
		while ( lFirstDayOfWeekCalendar.get(Calendar.YEAR) > lDisplayedCalendar.get(Calendar.YEAR)
			 || (lFirstDayOfWeekCalendar.get(Calendar.YEAR) == lDisplayedCalendar.get(Calendar.YEAR) && lFirstDayOfWeekCalendar.get(Calendar.WEEK_OF_YEAR) > lDisplayedCalendar.get(Calendar.WEEK_OF_YEAR))
			  )
		{
			lFirstDayOfWeekCalendar.add(Calendar.DATE, -7);
		}
		while ( lFirstDayOfWeekCalendar.get(Calendar.YEAR) < lDisplayedCalendar.get(Calendar.YEAR)
				 || (lFirstDayOfWeekCalendar.get(Calendar.YEAR) == lDisplayedCalendar.get(Calendar.YEAR) && lFirstDayOfWeekCalendar.get(Calendar.WEEK_OF_YEAR) < lDisplayedCalendar.get(Calendar.WEEK_OF_YEAR))
				  )
		{
			lFirstDayOfWeekCalendar.add(Calendar.DATE, 7);
		}
		
		// done
		return lFirstDayOfWeekCalendar;
	}

	/**
	 * 
	 * @param c1
	 * @param c2
	 * @return
	 */
	private boolean isSameDay(Calendar c1, Calendar c2)
	{
		return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
			&& c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
			&& c1.get(Calendar.DATE) == c2.get(Calendar.DATE)
			 ; 
	}

	/**
	 * 
	 * @param tracks
	 * @param appointmentPane
	 * @return
	 */
	private int determineTrackWhereAppointmentCanBeAdded(List<List<AppointmentPane>> tracks, AppointmentPane appointmentPane)
	{
		int lTrackNr = 0;
		while (true)
		{
			// make sure there is a arraylist for this track
			if (lTrackNr == tracks.size()) tracks.add(new ArrayList<AppointmentPane>());
			
			// scan all existing appointments in this track and see if there is an overlap
			if (checkIfTheAppointmentOverlapsAnAppointmentAlreadyInThisTrack(tracks, lTrackNr, appointmentPane) == false)
			{
				// no overlap, it can be added here
				return lTrackNr;
			}

			// overlap, try next track
			lTrackNr++;
		}
	}
	
	/**
	 * 
	 * @param tracks
	 * @param tracknr
	 * @param appointmentPane
	 * @return
	 */
	private boolean checkIfTheAppointmentOverlapsAnAppointmentAlreadyInThisTrack(List<List<AppointmentPane>> tracks, int tracknr, AppointmentPane appointmentPane)
	{
		// get the track
		List<AppointmentPane> lTrack = tracks.get(tracknr);
		
		// scan all existing appointments in this track
		for (AppointmentPane lAppointmentPane : lTrack)
		{
			// There is an overlap:
			// if the start time of the already placed appointment is before or equals the new appointment's end time 
			// and the end time of the already placed appointment is after or equals the new appointment's start time
			// ...PPPPPPPPP...
			// .NNNN.......... -> Ps <= Ne & Pe >= Ns -> overlap
			// .....NNNNN..... -> Ps <= Ne & Pe >= Ns -> overlap
			// ..........NNN.. -> Ps <= Ne & Pe >= Ns -> overlap
			// .NNNNNNNNNNNNN. -> Ps <= Ne & Pe >= Ns -> overlap
			// .N............. -> false	& Pe >= Ns -> no overlap
			// .............N. -> Ps <= Ne & false	-> no overlap
			if ( (lAppointmentPane.start.equals(appointmentPane.start) || lAppointmentPane.start.before(appointmentPane.end)) 
			  && (lAppointmentPane.end.equals(appointmentPane.start) || lAppointmentPane.end.after(appointmentPane.start))
			   )
			{
				// overlap
				return true;
			}
		}
		
		// no overlap
		return false;
	}
	
	/**
	 * 
	 * @param c
	 * @return
	 */
	private Calendar setTimeTo0000(Calendar c)
	{
		// start
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c;
	}
	
	/**
	 * 
	 * @param c
	 * @return
	 */
	private Calendar setTimeTo2359(Calendar c)
	{
		c.set(Calendar.HOUR_OF_DAY, 23);
		c.set(Calendar.MINUTE, 59);
		c.set(Calendar.SECOND, 59);
		c.set(Calendar.MILLISECOND, 999);
		return c;
	}
	
	/**
	 * 
	 * @param c
	 * @param minutes
	 * @return
	 */
	private Calendar setTimeToNearestMinutes(Calendar c, int minutes)
	{
		// align to X minutes accuracy
		c.set(Calendar.MILLISECOND, 0);
		c.set(Calendar.SECOND, 0);
		int lMinutes = c.get(Calendar.MINUTE) % minutes;
		if (lMinutes < (minutes/2)) c.add(Calendar.MINUTE, -1 * lMinutes);
		else c.add(Calendar.MINUTE, minutes - lMinutes);
		return c;
	}
	
	/**
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	private Calendar copyYMD(Calendar from, Calendar to)
	{
		to.set(Calendar.YEAR, from.get(Calendar.YEAR));
		to.set(Calendar.MONTH, from.get(Calendar.MONTH));
		to.set(Calendar.DATE, from.get(Calendar.DATE));
		return to;
	}
	
	class ImageButton extends ImageView
	{
		public ImageButton(Image i)
		{
			super(i);
			setPickOnBounds(true);
			setOnMouseEntered(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{						
						ImageButton.this.setCursor(Cursor.HAND);
					}
				}
			});
			setOnMouseExited(new EventHandler<MouseEvent>()
			{
				@Override
				public void handle(MouseEvent mouseEvent)
				{
					if (!mouseEvent.isPrimaryButtonDown())
					{
						ImageButton.this.setCursor(Cursor.DEFAULT);
					}
				}
			});
		}
	}
}
