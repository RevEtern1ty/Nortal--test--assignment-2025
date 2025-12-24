package com.nortal.library.core;

import static com.nortal.library.core.enums.ErrorReason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import com.nortal.library.core.sevice.LibraryService;
import java.util.Optional;
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

  private static final String BOOK_ID = "book1";
  private static final String MEMBER_ID = "member1";

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
    Book book = new Book(BOOK_ID, ":Title");
    book.setLoanedTo("Other");

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(ALREADY_LOANED.name(), result.reason());
  }

  @Test
  void shouldFailWhenMemberNotFound() {
    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(new Book(BOOK_ID, ":Title")));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(false);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertFalse(result.ok());
    assertEquals(MEMBER_NOT_FOUND.name(), result.reason());
  }

  @Test
  void shouldBorrowBookSuccessfully() {
    Book book = new Book(BOOK_ID, ":Title");

    when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
    when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
    when(bookRepository.countLoanedTo(MEMBER_ID)).thenReturn(0);

    LibraryService.Result result = libraryService.borrowBook(BOOK_ID, MEMBER_ID);

    assertTrue(result.ok());
    verify(bookRepository).save(any(Book.class));
    assertEquals(MEMBER_ID, book.getLoanedTo());
    assertNotNull(book.getDueDate());
  }
}
