package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;

import static de.seuhd.campuscoffee.domain.tests.TestFixtures.getApprovalConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit and integration tests for the operations related to reviews.
 */
@ExtendWith(MockitoExtension.class)
public class ReviewServiceTest {
    private final ApprovalConfiguration approvalConfiguration = getApprovalConfiguration();

    @Mock
    private ReviewDataService reviewDataService;

    @Mock
    private UserDataService userDataService;

    @Mock
    private PosDataService posDataService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void beforeEach() {
        reviewService = new ReviewServiceImpl(
                reviewDataService, userDataService, posDataService, approvalConfiguration
        );
    }

    @Test
    void approvalFailsIfUserIsAuthor() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        assertNotNull(review.author().id());
        when(userDataService.getById(review.author().id())).thenReturn(review.author());
        assertNotNull(review.id());
        when(reviewDataService.getById(review.id())).thenReturn(review);

        // when, then
        assertThrows(ValidationException.class, () -> reviewService.approve(review, review.author().getId()));
        verify(userDataService).getById(review.author().id());
        verify(reviewDataService).getById(review.getId());
    }

    @Test
    void approvalSuccessfulIfUserIsNotAuthor() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(2)
                .approved(false)
                .build();
        User user = TestFixtures.getUserFixtures().getLast();
        assertNotNull(user.getId());
        when(userDataService.getById(user.getId())).thenReturn(user);
        assertNotNull(review.getId());
        when(reviewDataService.getById(review.getId())).thenReturn(review);
        when(reviewDataService.upsert(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Review approvedReview = reviewService.approve(review, user.getId());

        // then
        verify(userDataService).getById(user.getId());
        verify(reviewDataService).getById(review.getId());
        verify(reviewDataService).upsert(any(Review.class));
        assertThat(approvedReview.approvalCount()).isEqualTo(review.approvalCount() + 1);
        assertThat(approvedReview.approved()).isTrue();
    }

    @Test
    void getApprovedByPos() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        assertNotNull(pos.getId());
        List<Review> reviews = TestFixtures.getReviewFixtures().stream()
                .map(review -> review.toBuilder()
                        .pos(pos)
                        .approvalCount(3)
                        .approved(true)
                        .build())
                .toList();
        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.filter(pos, true)).thenReturn(reviews);

        // when
        List<Review> retrievedReviews = reviewService.filter(Objects.requireNonNull(pos.getId()), true);

        // then
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, true);
        assertThat(retrievedReviews).hasSize(reviews.size());
    }

    @Test
    void createReviewPosDoesNotExistException() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        assertNotNull(review.pos().getId());
        when(posDataService.getById(review.pos().getId())).thenThrow(
                new NotFoundException(review.pos().getClass(), review.pos().getId())
        );

        // when, then
        assertThrows(NotFoundException.class, () -> reviewService.upsert(review));
        verify(posDataService).getById(review.pos().getId());
    }

    @Test
    void userCannotCreateMoreThanOneReviewPerPos() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        Pos pos = review.pos();
        User author = review.author();
        assertNotNull(pos.getId());

        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.filter(pos, author)).thenReturn(List.of(review));

        // when, then
        assertThrows(ValidationException.class, () -> reviewService.upsert(review)
        );
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, author);
    }

    @Test
    void testUpdateApprovalStatusForUnapprovedReview() {
        // given
        Review unapprovedReview = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(2)
                .approved(false)
                .build();

        // when
        Review updatedReview = reviewService.updateApprovalStatus(unapprovedReview);

        // then
        assertFalse(updatedReview.approved());

        // when
        Review approvedReview = unapprovedReview.toBuilder()
                .approvalCount(approvalConfiguration.minCount())
                .build();

        // when
        updatedReview = reviewService.updateApprovalStatus(approvedReview);

        // then
        assertTrue(updatedReview.approved());
    }

    @Test
    void upsertFirstReviewPerPosSucceeds() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        Pos pos = Objects.requireNonNull(review.pos());
        User author = Objects.requireNonNull(review.author());
        assertNotNull(pos.getId());
        assertNotNull(author.getId());

        // POS exists
        when(posDataService.getById(pos.getId())).thenReturn(pos);
        // author has not reviewed this POS before
        when(reviewDataService.filter(pos, author)).thenReturn(List.of());

        // if the fixture has an ID, the base CrudServiceImpl will validate existence
        if (review.getId() != null) {
            when(reviewDataService.getById(review.getId())).thenReturn(review);
        }

        when(reviewDataService.upsert(review)).thenReturn(review);

        // when
        Review result = reviewService.upsert(review);

        // then
        assertThat(result).isSameAs(review);
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, author);
        verify(reviewDataService).upsert(review);
    }

    @Test
    void approvalDoesNotReachQuorumKeepsUnapproved() {
        // given
        Review base = TestFixtures.getReviewFixtures().getFirst();

        int minCount = approvalConfiguration.minCount();
        int before = Math.max(0, minCount - 2); // ensure after increment still < minCount for typical configs
        if (minCount <= 1) {
            before = 0; // best effort: with minCount 0/1, the review will be approved quickly anyway
        }

        Review review = base.toBuilder()
                .approvalCount(before)
                .approved(false)
                .build();

        // choose a user that is not the author
        List<User> users = TestFixtures.getUserFixtures();
        User user = users.stream()
                .filter(u -> u.getId() != null && !u.getId().equals(review.author().getId()))
                .findFirst()
                .orElseGet(users::getLast);

        assertNotNull(user.getId());
        when(userDataService.getById(user.getId())).thenReturn(user);

        Objects.requireNonNull(review.getId());
        Review stored = review.toBuilder().build();
        when(reviewDataService.getById(review.getId())).thenReturn(stored);

        when(reviewDataService.upsert(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Review result = reviewService.approve(review, user.getId());

        // then
        assertFalse(result.approved());
        verify(reviewDataService).upsert(any(Review.class));
    }

    @Test
    void approvalReachesQuorumMarksApproved() {
        // given
        Review base = TestFixtures.getReviewFixtures().getFirst();

        int minCount = approvalConfiguration.minCount();
        int before = Math.max(0, minCount - 1); // after increment => >= minCount

        Review review = base.toBuilder()
                .approvalCount(before)
                .approved(false)
                .build();

        // choose a user that is not the author
        List<User> users = TestFixtures.getUserFixtures();
        User user = users.stream()
                .filter(u -> u.getId() != null && !u.getId().equals(review.author().getId()))
                .findFirst()
                .orElseGet(users::getLast);

        assertNotNull(user.getId());
        when(userDataService.getById(user.getId())).thenReturn(user);

        Objects.requireNonNull(review.getId());
        Review stored = review.toBuilder().build();
        when(reviewDataService.getById(review.getId())).thenReturn(stored);

        when(reviewDataService.upsert(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Review result = reviewService.approve(review, user.getId());

        // then
        assertTrue(result.approved());
        verify(reviewDataService).upsert(any(Review.class));
    }

}
