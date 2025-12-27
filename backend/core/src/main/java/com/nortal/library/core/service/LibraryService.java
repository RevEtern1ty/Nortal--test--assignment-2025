package com.nortal.library.core.service;

import static com.nortal.library.core.enums.ErrorReason.*;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LibraryService {
  private static final int MAX_LOANS = 5;
  private static final int DEFAULT_LOAN_DAYS = 14;

  private final BookRepository bookRepository;
  private final MemberRepository memberRepository;

  @Transactional
  public Result borrowBook(String bookId, String memberId) {
    Optional<Book> bookOptional = bookRepository.findById(bookId);

    // Check if the book and member exists
    if (bookOptional.isEmpty()) return Result.failure(BOOK_NOT_FOUND.name());
    if (memberNotFound(memberId)) return Result.failure(MEMBER_NOT_FOUND.name());

    Book book = bookOptional.get();

    // A book already on loan must not be loaned again.
    if (isAlreadyLoaned(book)) return Result.failure(ALREADY_LOANED.name());

    // If a reservation queue exists, only the head of the queue should receive the book.
    if (isQueueViolation(book, memberId)) return Result.failure(NOT_FIRST_IN_QUEUE.name());

    // Borrow-limit enforcement
    if (hasReachedLoanLimit(memberId)) return Result.failure(BORROW_LIMIT.name());

    // Assign the book to the member and saves changes
    loanBookToMember(book, memberId);
    return Result.success();
  }

  @Transactional
  public ResultWithNext returnBook(String bookId, String memberId) {
    Optional<Book> bookOptional = bookRepository.findById(bookId);

    // Check if the book exists
    if (bookOptional.isEmpty()) return ResultWithNext.failure();

    Book book = bookOptional.get();

    // Returns should only succeed when initiated by the current borrower.
    if (memberNotFound(memberId)) return ResultWithNext.failure();
    if (!isReturnedByCurrentBorrower(book, memberId)) return ResultWithNext.failure();

    releaseBookFromMember(book);

    // If reservation queue exists, try handing the book to the next valid reserver
    if(hasReservationQueue(book)){
      String next = nextValidatedReserver(book,memberId);
      if (next != null){
        loanBookToMember(book,next);
        return ResultWithNext.success(next); // return with info on who got the book next
      }
    }
    bookRepository.save(book);
    return ResultWithNext.success(null); // success, but no next borrower
  }

  @Transactional
  public Result reserveBook(String bookId, String memberId) {
    Optional<Book> bookOptional = bookRepository.findById(bookId);

        // Check if the book and member exists

    if (bookOptional.isEmpty()) return Result.failure(BOOK_NOT_FOUND.name());
    if (memberNotFound(memberId)) return Result.failure(MEMBER_NOT_FOUND.name());

    Book book = bookOptional.get();

        // Check duplicate reservations
    if (isAlreadyReserved(book,memberId)){
      return Result.failure(ALREADY_RESERVED.name());
    }

      // Check if loan for book and member are available
     if (isAvailable(book)) {
       if(isQueueViolation(book, memberId)) return Result.failure(NOT_FIRST_IN_QUEUE.name());
       if (hasReachedLoanLimit(memberId)) return  Result.failure(BORROW_LIMIT.name());
     }

      // Add to reservation queue
    loanBookToMember(book, memberId);
    bookRepository.save(book);
    return Result.success();
  }

  @Transactional
  public Result cancelReservation(String bookId, String memberId) {
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure("MEMBER_NOT_FOUND");
    }

    Book entity = book.get();
    boolean removed = entity.getReservationQueue().remove(memberId);
    if (!removed) {
      return Result.failure("NOT_RESERVED");
    }
    bookRepository.save(entity);
    return Result.success();
  }

  public List<Book> searchBooks(String titleContains, Boolean availableOnly, String loanedTo) {
    return bookRepository.findAll().stream()
        .filter(
            b ->
                titleContains == null
                    || b.getTitle().toLowerCase().contains(titleContains.toLowerCase()))
        .filter(b -> loanedTo == null || loanedTo.equals(b.getLoanedTo()))
        .filter(
            b ->
                availableOnly == null
                    || (availableOnly ? b.getLoanedTo() == null : b.getLoanedTo() != null))
        .toList();
  }

  public List<Book> overdueBooks(LocalDate today) {
    return bookRepository.findAll().stream()
        .filter(b -> b.getLoanedTo() != null)
        .filter(b -> b.getDueDate() != null && b.getDueDate().isBefore(today))
        .toList();
  }

  @Transactional
  public Result extendLoan(String bookId, int days) {
    if (days == 0) {
      return Result.failure("INVALID_EXTENSION");
    }
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    Book entity = book.get();
    if (entity.getLoanedTo() == null) {
      return Result.failure("NOT_LOANED");
    }
    LocalDate baseDate =
        entity.getDueDate() == null
            ? LocalDate.now().plusDays(DEFAULT_LOAN_DAYS)
            : entity.getDueDate();
    entity.setDueDate(baseDate.plusDays(days));
    bookRepository.save(entity);
    return Result.success();
  }

  public MemberSummary memberSummary(String memberId) {
    if (!memberRepository.existsById(memberId)) {
      return new MemberSummary(false, "MEMBER_NOT_FOUND", List.of(), List.of());
    }
    List<Book> books = bookRepository.findAll();
    List<Book> loans = new ArrayList<>();
    List<ReservationPosition> reservations = new ArrayList<>();
    for (Book book : books) {
      if (memberId.equals(book.getLoanedTo())) {
        loans.add(book);
      }
      int idx = book.getReservationQueue().indexOf(memberId);
      if (idx >= 0) {
        reservations.add(new ReservationPosition(book.getId(), idx));
      }
    }
    return new MemberSummary(true, null, loans, reservations);
  }

  public Optional<Book> findBook(String id) {
    return bookRepository.findById(id);
  }

  public List<Book> allBooks() {
    return bookRepository.findAll();
  }

  public List<Member> allMembers() {
    return memberRepository.findAll();
  }

  @Transactional
  public Result createBook(String id, String title) {
    if (id == null || title == null) {
      return Result.failure("INVALID_REQUEST");
    }
    bookRepository.save(new Book(id, title));
    return Result.success();
  }

  @Transactional
  public Result updateBook(String id, String title) {
    Optional<Book> existing = bookRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    if (title == null) {
      return Result.failure("INVALID_REQUEST");
    }
    Book book = existing.get();
    book.setTitle(title);
    bookRepository.save(book);
    return Result.success();
  }

  @Transactional
  public Result deleteBook(String id) {
    Optional<Book> existing = bookRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    Book book = existing.get();
    bookRepository.delete(book);
    return Result.success();
  }

  @Transactional
  public Result createMember(String id, String name) {
    if (id == null || name == null) {
      return Result.failure("INVALID_REQUEST");
    }
    memberRepository.save(new Member(id, name));
    return Result.success();
  }

  @Transactional
  public Result updateMember(String id, String name) {
    Optional<Member> existing = memberRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("MEMBER_NOT_FOUND");
    }
    if (name == null) {
      return Result.failure("INVALID_REQUEST");
    }
    Member member = existing.get();
    member.setName(name);
    memberRepository.save(member);
    return Result.success();
  }

  @Transactional
  public Result deleteMember(String id) {
    Optional<Member> existing = memberRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("MEMBER_NOT_FOUND");
    }
    memberRepository.delete(existing.get());
    return Result.success();
  }

  public record Result(boolean ok, String reason) {

    public static Result success() {
      return new Result(true, null);
    }

    public static Result failure(String reason) {
      return new Result(false, reason);
    }

  }
  public record ResultWithNext(boolean ok, String nextMemberId) {

    public static ResultWithNext success(String nextMemberId) {
      return new ResultWithNext(true, nextMemberId);
    }

    public static ResultWithNext failure() {
      return new ResultWithNext(false, null);
    }

  }
  public record MemberSummary(
      boolean ok, String reason, List<Book> loans, List<ReservationPosition> reservations) {}

  public record ReservationPosition(String bookId, int position) {}


  // 1. borrowBook helpers

  private boolean memberNotFound(String memberId) {
    return !memberRepository.existsById(memberId);
  }

  private boolean isAlreadyLoaned(Book book) {
    return book.getLoanedTo() != null;
  }

  private boolean isQueueViolation(Book book, String memberId) {

    // Checks  whether the member is violating the reservation queue rules
    // Returns true if queue is not empty and also the member is not at the front

    List<String> queue = book.getReservationQueue();
    return !queue.isEmpty() && !queue.getFirst().equals(memberId);
  }

  private boolean hasReachedLoanLimit(String memberId) {

    // Returns true if the member has reached or exceeded the maximum allowed number of active book
    // loans.
    // Uses a custom @Query method to count how many books are currently loaned to the member.

    return bookRepository.countLoanedTo(memberId) >= MAX_LOANS;
  }

  private void loanBookToMember(Book book, String memberId) {

    // Assigns the book to the member, sets the due date,
    // removes the member from the reservation queue, and saves the changes.

    book.setLoanedTo(memberId);
    book.setDueDate(LocalDate.now().plusDays(DEFAULT_LOAN_DAYS));
    book.getReservationQueue().remove(memberId);
    bookRepository.save(book);
  }

  // 2. returnBook helper methods

  private void releaseBookFromMember(Book book){
    book.setLoanedTo(null);
    book.setDueDate(null);
  }
  private boolean isReturnedByCurrentBorrower(Book book, String memberId) {
    return book.getLoanedTo() != null && book.getLoanedTo().equals(memberId);
  }

  private boolean hasReservationQueue (Book book){
    return !book.getReservationQueue().isEmpty();
  }

  private  String nextValidatedReserver ( Book book, String memberId){
    Iterator<String> iterator = book.getReservationQueue().iterator();
    while (iterator.hasNext()){
      String candidate = iterator.next();

      // Skip if candidate doesn't exist
      if (!memberRepository.existsById(candidate)){
        iterator.remove();
        continue;
      }

      // Skip if candidate reached loan limit
      if (hasReachedLoanLimit(candidate)) continue;

      // Skip if candidate is the same person returning the book
      if (candidate.equals(memberId)){
        continue;
      }

      iterator.remove(); // When reserved -remove from queue
      return candidate;
    }
    return null;
  }

  // 3. reserveBook helper method

  private boolean isAlreadyReserved(Book book,String memberId){
    return book.getReservationQueue().contains(memberId);
  }

  private boolean isAvailable(Book book) {
    return book.getLoanedTo() == null;
  }


}
