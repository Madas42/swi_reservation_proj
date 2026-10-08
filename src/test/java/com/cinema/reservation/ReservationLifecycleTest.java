package com.cinema.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class ReservationLifecycleTest {

    @Autowired
    private FilmRepository filmRepository;
    @Autowired
    private CinemaRoomRepository cinemaRoomRepository;
    @Autowired
    private ScreeningRepository screeningRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservedSeatRepository reservedSeatRepository;
    @Autowired
    private ReservationManagement reservationManagement;
    @Autowired
    private ApprovalWorkflow approvalWorkflow;
    @Autowired
    private ExpirationManager expirationManager;
    @MockBean
    private NotificationClient notificationClient;

    @Test
    @Transactional
    void createDraftCreatesUnallocatedDraftReservation() {
        TestData data = testData(LocalDateTime.now().plusHours(2));

        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId(), data.seats().get(1).getId()),
                "Ada Lovelace", "ada@example.com");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DRAFT);
        assertThat(reservation.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(reservation.getRequestedSeatIds()).containsExactlyInAnyOrder(
                data.seats().get(0).getId(), data.seats().get(1).getId());
        assertThat(reservedSeatRepository.findByReservation_Id(reservation.getId())).isEmpty();
    }

    @Test
    @Transactional
    void confirmAllocatesSeatsAndConfirmsReservation() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Grace Hopper", "grace@example.com");

        ReservationManagement.Outcome outcome = reservationManagement.confirm(reservation.getId());

        assertThat(outcome.success()).isTrue();
        Reservation loaded = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservedSeatRepository.findByReservation_Id(reservation.getId()))
                .extracting(reservedSeat -> reservedSeat.getSeat().getId())
                .containsExactly(data.seats().get(0).getId());
    }

    @Test
    @Transactional
    void confirmExpiredDraftMarksReservationExpired() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Customer customer = customerRepository.save(new Customer("Alan", "alan@example.com"));
        Reservation expired = reservationRepository.save(new Reservation(
                customer, data.screening(), ReservationStatus.DRAFT,
                LocalDateTime.now().minusMinutes(1)));
        expired.requestSeats(List.of(data.seats().get(0)));
        reservationRepository.save(expired);

        ReservationManagement.Outcome outcome = reservationManagement.confirm(expired.getId());

        assertThat(outcome.success()).isFalse();
        assertThat(reservationRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservedSeatRepository.findByReservation_Id(expired.getId())).isEmpty();
    }

    @Test
    @Transactional
    void confirmRejectsSeatTakenByAnotherConfirmedReservation() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Reservation first = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "First Customer", "first@example.com");
        Reservation second = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Second Customer", "second@example.com");

        assertThat(reservationManagement.confirm(first.getId()).success()).isTrue();
        ReservationManagement.Outcome outcome = reservationManagement.confirm(second.getId());

        assertThat(outcome.success()).isFalse();
        assertThat(reservationRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.DRAFT);
        assertThat(reservedSeatRepository.findByReservation_Id(second.getId())).isEmpty();
    }

    @Test
    @Transactional
    void cancelConfirmedReservationFreesSeats() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Cancel Customer", "cancel@example.com");
        reservationManagement.confirm(reservation.getId());

        ReservationManagement.Outcome outcome = reservationManagement.cancel(reservation.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservedSeatRepository.findByReservation_Screening_IdAndReservation_Status(
                data.screening().getId(), ReservationStatus.CONFIRMED)).isEmpty();
    }

    @Test
    @Transactional
    void cancelRejectedWhenLessThanThirtyMinutesRemain() {
        TestData data = testData(LocalDateTime.now().plusMinutes(10));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Late Customer", "late@example.com");
        reservationManagement.confirm(reservation.getId());

        ReservationManagement.Outcome outcome = reservationManagement.cancel(reservation.getId());

        assertThat(outcome.success()).isFalse();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @Transactional
    void cancelIsIdempotentForAlreadyCancelledReservation() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Twice Customer", "twice@example.com");
        reservationManagement.cancel(reservation.getId());

        ReservationManagement.Outcome outcome = reservationManagement.cancel(reservation.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.message()).isEqualTo("Your reservation has been cancelled.");
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @Transactional
    void cancelledSeatCanBeRebookedByAnotherCustomer() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Reservation first = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "First Customer", "rebook-first@example.com");
        assertThat(reservationManagement.confirm(first.getId()).success()).isTrue();
        assertThat(reservationManagement.cancel(first.getId()).success()).isTrue();

        Reservation second = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Second Customer", "rebook-second@example.com");
        ReservationManagement.Outcome outcome = reservationManagement.confirm(second.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(reservationRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservedSeatRepository.findByReservation_Screening_IdAndReservation_Status(
                data.screening().getId(), ReservationStatus.CONFIRMED))
                .extracting(reservedSeat -> reservedSeat.getSeat().getId())
                .containsExactly(data.seats().get(0).getId());
    }

    @Test
    @Transactional
    void createDraftRejectsPastScreening() {
        TestData data = testData(LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Past Customer", "past@example.com"))
                .isInstanceOf(ReservationRuleException.class);
    }

    @Test
    @Transactional
    void createDraftRejectsSecondActiveReservationForSameCustomerAndScreening() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Dup Customer", "dup@example.com");

        assertThatThrownBy(() -> reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(1).getId()),
                "Dup Customer", "dup@example.com"))
                .isInstanceOf(ReservationRuleException.class);
    }

    @Test
    @Transactional
    void confirmLargeDraftRequiresAdminApproval() {
        TestData data = largeTestData(LocalDateTime.now().plusHours(2));
        List<Long> seatIds = data.seats().stream().map(Seat::getId).toList();
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(), seatIds,
                "Big Customer", "big@example.com");

        ReservationManagement.Outcome outcome = reservationManagement.confirm(reservation.getId());

        assertThat(outcome.success()).isTrue();
        Reservation loaded = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
        assertThat(reservedSeatRepository.findByReservation_Id(reservation.getId()))
                .hasSize(seatIds.size());
    }

    @Test
    @Transactional
    void approvePendingReservationConfirmsIt() {
        TestData data = largeTestData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                data.seats().stream().map(Seat::getId).toList(),
                "Approve Customer", "approve@example.com");
        reservationManagement.confirm(reservation.getId());

        ReservationManagement.Outcome outcome = approvalWorkflow.approve(reservation.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservedSeatRepository.findByReservation_Id(reservation.getId())).isNotEmpty();
    }

    @Test
    @Transactional
    void rejectPendingReservationReleasesSeats() {
        TestData data = largeTestData(LocalDateTime.now().plusHours(2));
        List<Long> seatIds = data.seats().stream().map(Seat::getId).toList();
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(), seatIds,
                "Reject Customer", "reject@example.com");
        reservationManagement.confirm(reservation.getId());

        ReservationManagement.Outcome outcome = approvalWorkflow.reject(reservation.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservedSeatRepository.findByReservation_Id(reservation.getId())).isEmpty();
        assertThat(reservedSeatRepository
                .findByReservation_Screening_IdAndReservation_StatusIn(
                        data.screening().getId(),
                        List.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING_APPROVAL)))
                .isEmpty();
    }

    @Test
    @Transactional
    void pendingApprovalSeatsBlockOtherReservations() {
        TestData data = largeTestData(LocalDateTime.now().plusHours(2));
        Reservation small = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Blocked Customer", "blocked@example.com");
        Reservation large = reservationManagement.createDraft(
                data.screening().getId(),
                data.seats().stream().map(Seat::getId).toList(),
                "Blocking Customer", "blocking@example.com");
        assertThat(reservationManagement.confirm(large.getId()).success()).isTrue();
        assertThat(reservationRepository.findById(large.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_APPROVAL);

        ReservationManagement.Outcome outcome = reservationManagement.confirm(small.getId());

        assertThat(outcome.success()).isFalse();
        assertThat(reservationRepository.findById(small.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.DRAFT);
        assertThat(reservedSeatRepository.findByReservation_Id(small.getId())).isEmpty();
    }

    @Test
    @Transactional
    void cancelPendingApprovalReservationReleasesSeats() {
        TestData data = largeTestData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                data.seats().stream().map(Seat::getId).toList(),
                "Cancel Pending Customer", "cancelpending@example.com");
        reservationManagement.confirm(reservation.getId());

        ReservationManagement.Outcome outcome = reservationManagement.cancel(reservation.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservedSeatRepository.findByReservation_Id(reservation.getId())).isEmpty();
    }

    @Test
    @Transactional
    void approveAndRejectOnlyWorkOnPendingApproval() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                List.of(data.seats().get(0).getId()),
                "Draft Customer", "draftonly@example.com");

        assertThat(approvalWorkflow.approve(reservation.getId()).success()).isFalse();
        assertThat(approvalWorkflow.reject(reservation.getId()).success()).isFalse();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.DRAFT);
    }

    @Test
    @Transactional
    void backgroundJobExpiresStaleDraft() {
        TestData data = testData(LocalDateTime.now().plusHours(2));
        Customer customer = customerRepository.save(new Customer("Stale Customer", "stale@example.com"));
        Reservation stale = reservationRepository.save(new Reservation(
                customer, data.screening(), ReservationStatus.DRAFT,
                LocalDateTime.now().minusMinutes(1)));
        stale.requestSeats(List.of(data.seats().get(0)));
        reservationRepository.save(stale);

        expirationManager.expireStaleDrafts();

        assertThat(reservationRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @Transactional
    void approveSurvivesNotificationServiceFailure() {
        TestData data = largeTestData(LocalDateTime.now().plusHours(2));
        Reservation reservation = reservationManagement.createDraft(
                data.screening().getId(),
                data.seats().stream().map(Seat::getId).toList(),
                "Notify Customer", "notify@example.com");
        reservationManagement.confirm(reservation.getId());
        doThrow(new IllegalStateException("notification service down"))
                .when(notificationClient).send(any(), any(), any());

        ReservationManagement.Outcome outcome = approvalWorkflow.approve(reservation.getId());

        assertThat(outcome.success()).isTrue();
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        verify(notificationClient, times(NotificationIntegration.MAX_SEND_ATTEMPTS))
                .send(any(), any(), any());
    }

    private TestData testData(LocalDateTime screeningStart) {
        Film film = filmRepository.save(new Film("Lifecycle Film", "Test", 90));
        CinemaRoom room = cinemaRoomRepository.save(new CinemaRoom("Lifecycle Room", 1, 3));
        Screening screening = screeningRepository.save(new Screening(
                film, room, screeningStart, screeningStart.plusMinutes(90)));
        List<Seat> seats = seatRepository.saveAll(IntStream.rangeClosed(1, 3)
                .mapToObj(number -> new Seat(room, 1, number))
                .toList());
        return new TestData(screening, seats);
    }

    private TestData largeTestData(LocalDateTime screeningStart) {
        Film film = filmRepository.save(new Film("Large Room Film", "Test", 90));
        CinemaRoom room = cinemaRoomRepository.save(new CinemaRoom("Large Room", 2, 6));
        Screening screening = screeningRepository.save(new Screening(
                film, room, screeningStart, screeningStart.plusMinutes(90)));
        List<Seat> seats = seatRepository.saveAll(IntStream.rangeClosed(1, 6)
                .boxed()
                .flatMap(row -> IntStream.rangeClosed(1, 2)
                        .mapToObj(number -> new Seat(room, row, number)))
                .toList());
        return new TestData(screening, seats);
    }

    private record TestData(Screening screening, List<Seat> seats) {
    }
}
