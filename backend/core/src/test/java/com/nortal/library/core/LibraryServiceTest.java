package com.nortal.library.core;

import static com.nortal.library.core.enums.ErrorReason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import com.nortal.library.core.service.LibraryService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

  @Mock private BookRepository bookRepository;

  @Mock private MemberRepository memberRepository;

  @InjectMocks private LibraryService libraryService;

  private Book book;
  private static final String BOOK_ID = "book1";
  private static final String MEMBER_ID = "member1";
  private static final String NEXT_USER = "nextUser";
  private static final String VALID_USER = "validUser";

  @BeforeEach
  void setup() {
    book = new Book(BOOK_ID, ":Title");
  }

  // borrowBook Tests

  @Test
  void shouldFailWhenBookNotFound() {
    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.empty());

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(BOOK_NOT_FOUND.name(), result.reason());
  }

  @Test
  void shouldFailWhenBookAlreadyLoaned() {
    book.setLoanedTo(VALID_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(ALREADY_LOANED.name(), result.reason());
  }

  @Test
  void shouldFailWhenMemberNotFound() {
    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(false);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(MEMBER_NOT_FOUND.name(), result.reason());
  }

  @Test
  void shouldBorrowBookSuccessfully() {
    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
    when(bookRepository.countLoanedTo(MEMBER_ID)).thenReturn(0);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    verify(bookRepository).save(any(Book.class));
    assertEquals(MEMBER_ID, book.getLoanedTo());
    assertNotNull(book.getDueDate());
  }

  @Test
  void shouldFailBorrowWhenNotFirstInQueue() {
    book.getReservationQueue().add(NEXT_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(NOT_FIRST_IN_QUEUE.name(), result.reason());
    verify(bookRepository, never()).save(any());
  }

  // returnBook tests

  @Test
  void shouldFailReturnBookWhenReturnedByNonBorrower() {
    book.setLoanedTo(VALID_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.ResultWithNext result = libraryService.returnBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    verify(bookRepository, never()).save(any());
  }

  @Test
  void shouldReturnBookWithoutReservation() {
    book.setLoanedTo(MEMBER_ID);
    book.setDueDate(LocalDate.now().plusDays(7));

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.ResultWithNext result = libraryService.returnBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    assertNull(result.nextMemberId());
    assertNull(book.getLoanedTo());
    assertNull(book.getDueDate());
    verify(bookRepository).save(book);
  }

  @Test
  void shouldReturnBookToNextValidReserver() {
    book.setLoanedTo(MEMBER_ID);
    book.setDueDate(LocalDate.now().plusDays(5));
    book.getReservationQueue().add(NEXT_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
    when(memberRepository.existsById(NEXT_USER)).thenReturn(true);
    when(bookRepository.countLoanedTo(NEXT_USER)).thenReturn(0);

    LibraryService.ResultWithNext result = libraryService.returnBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    assertEquals(NEXT_USER, result.nextMemberId());
    verify(bookRepository).save(book);
    assertEquals(NEXT_USER, book.getLoanedTo());
    assertNotNull(book.getDueDate());
  }

  @Test
  void shouldFailReturnBookWhenBookNotFound() {
    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.empty());

    LibraryService.ResultWithNext result = libraryService.returnBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertNull(result.nextMemberId());
  }

  @Test
  void shouldFailReturnBookWhenNoCurrentBorrower() {
    book.setLoanedTo(null);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));

    LibraryService.ResultWithNext result = libraryService.returnBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
  }

  @Test
  void shouldSkipInvalidReserverAndAssignToNext() {
    book.setLoanedTo(MEMBER_ID);
    book.getReservationQueue().add("ghost");
    book.getReservationQueue().add(VALID_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
    when(memberRepository.existsById("ghost")).thenReturn(false);
    when(memberRepository.existsById(VALID_USER)).thenReturn(true);
    when(bookRepository.countLoanedTo(VALID_USER)).thenReturn(0);

    LibraryService.ResultWithNext result = libraryService.returnBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    assertFalse(book.getReservationQueue().contains("ghost"));
    assertFalse(book.getReservationQueue().contains(VALID_USER));
    assertEquals(VALID_USER, result.nextMemberId());
    verify(bookRepository).save(book);
  }

  // reserveBook tests

  @Test
  void shouldFailOnDuplicateReservation() {
    book.getReservationQueue().add(MEMBER_ID);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.Result result = libraryService.reserveBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(ALREADY_RESERVED.name(), result.reason());
  }

  @Test
  void shouldLoanIfAvailableAndValid() {
    book.setLoanedTo(null);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
    when(bookRepository.countLoanedTo(MEMBER_ID)).thenReturn(0);

    LibraryService.Result result = libraryService.reserveBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    assertEquals(MEMBER_ID, book.getLoanedTo());
    assertNotNull(book.getDueDate());
    verify(bookRepository).save(book);
  }

  @Test
  void shouldAddToQueueIfLoaned() {
    book.setLoanedTo(VALID_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.Result result = libraryService.reserveBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    assertTrue(book.getReservationQueue().contains(MEMBER_ID));
    verify(bookRepository).save(book);
  }

  @Test
  void shouldAddIfQueueViolationOrLoanLimitExceeded() {
    book.setLoanedTo(VALID_USER);
    book.getReservationQueue().add(NEXT_USER);

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.Result result = libraryService.reserveBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    assertTrue(book.getReservationQueue().contains(MEMBER_ID));
    verify(bookRepository).save(book);
  }
}
