package io.sparkled.rest;

import io.sparkled.model.entity.ScheduledSong;
import io.sparkled.model.entity.Song;
import io.sparkled.music.SongSchedulerService;
import io.sparkled.persistence.scheduler.ScheduledSongPersistenceService;
import io.sparkled.viewmodel.ScheduledSongViewModel;
import io.sparkled.viewmodel.converter.ScheduledSongViewModelConverter;
import org.apache.commons.lang3.time.DateUtils;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static io.sparkled.model.entity.ScheduledSong.MIN_SECONDS_BETWEEN_SONGS;

@Path("/scheduledSongs")
public class ScheduledSongRestService extends RestService {

    private static final Logger logger = Logger.getLogger(ScheduledSongRestService.class.getName());
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final SongSchedulerService songSchedulerService;
    private final ScheduledSongPersistenceService scheduledSongPersistenceService;
    private final ScheduledSongViewModelConverter scheduledSongViewModelConverter;

    @Inject
    public ScheduledSongRestService(SongSchedulerService songSchedulerService,
                                    ScheduledSongPersistenceService scheduledSongPersistenceService,
                                    ScheduledSongViewModelConverter scheduledSongViewModelConverter) {
        this.songSchedulerService = songSchedulerService;
        this.scheduledSongPersistenceService = scheduledSongPersistenceService;
        this.scheduledSongViewModelConverter = scheduledSongViewModelConverter;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getScheduledSongsForDay(@QueryParam("date") String date) {
        if (date == null) {
            String message = "Date must be provided.";
            return getJsonResponse(Response.Status.BAD_REQUEST, message);
        }

        Date parsedDay;
        try {
            parsedDay = DAY_FORMAT.parse(date);
        } catch (ParseException e) {
            String message = "Date format must be '" + DAY_FORMAT.toPattern() + "'.";
            return getJsonResponse(Response.Status.BAD_REQUEST, message);
        }

        Date startDay = DateUtils.truncate(parsedDay, Calendar.DATE);
        Date endDay = DateUtils.addDays(startDay, 1);
        endDay = DateUtils.addMilliseconds(endDay, -1);
        List<ScheduledSong> scheduledSongs = scheduledSongPersistenceService.getScheduledSongs(startDay, endDay);
        return getJsonResponse(scheduledSongs);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response scheduleSong(ScheduledSongViewModel viewModel) {
        ScheduledSong scheduledSong = scheduledSongViewModelConverter.fromViewModel(viewModel);

        Date songStartTime = scheduledSong.getStartTime();
        Date earliestStartTime = DateUtils.addSeconds(new Date(), MIN_SECONDS_BETWEEN_SONGS);
        if (songStartTime == null || songStartTime.before(earliestStartTime)) {
            String message = "Songs must be scheduled at least " + MIN_SECONDS_BETWEEN_SONGS + " seconds in the future.";
            return getJsonResponse(Response.Status.BAD_REQUEST, message);
        }

        Song song = scheduledSong.getSong();
        if (song != null) {
            Date offsetStartTime = DateUtils.addSeconds(songStartTime, -MIN_SECONDS_BETWEEN_SONGS);
            int seconds = (int) Math.ceil(song.getDurationFrames() / song.getFramesPerSecond());
            seconds += MIN_SECONDS_BETWEEN_SONGS;
            Date endTime = DateUtils.addSeconds(offsetStartTime, seconds);
            List<ScheduledSong> overlappingSongs = scheduledSongPersistenceService.getScheduledSongs(offsetStartTime, endTime);

            if (!overlappingSongs.isEmpty()) {
                String message = "Scheduled songs cannot overlap, and must have at least a " + MIN_SECONDS_BETWEEN_SONGS + " second gap between them.";
                return getJsonResponse(Response.Status.BAD_REQUEST, message);
            }
        }

        boolean success = scheduledSongPersistenceService.saveScheduledSong(scheduledSong);
        if (success) {
            logger.info("Song scheduled: " + scheduledSong.getSong().getName());
            songSchedulerService.scheduleNextSong();
        }
        return getResponse(success ? Response.Status.OK : Response.Status.BAD_REQUEST);
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unscheduleSong(@PathParam("id") int id) {
        boolean success = scheduledSongPersistenceService.removeScheduledSong(id);
        return getResponse(success ? Response.Status.OK : Response.Status.BAD_REQUEST);
    }
}