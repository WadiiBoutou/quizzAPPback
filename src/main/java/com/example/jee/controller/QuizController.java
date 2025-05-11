package com.example.jee.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jee.beans.Answer;
import com.example.jee.beans.Participant;
import com.example.jee.beans.Question;
import com.example.jee.beans.Quiz;
import com.example.jee.beans.User;
import com.example.jee.beans.UserAnswer;
import com.example.jee.repository.ParticipantRepository;
import com.example.jee.repository.QuizRepository;
import com.example.jee.repository.UserAnswerRepository;
import com.example.jee.repository.UserRepository;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private static final Logger logger = LoggerFactory.getLogger(QuizController.class);

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAnswerRepository userAnswerRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @PostMapping
    public Quiz createQuiz(@RequestBody Quiz quiz, @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
    
        // Fetch the user from the database using the username of the authenticated user
        User user = userRepository.findByUsername(currentUser.getUsername());
        if (user == null) {
            throw new RuntimeException("Authenticated user not found in the database");
        }
    
        // Set the creator of the quiz
        quiz.setCreatedBy(user);
    
        return quizRepository.save(quiz);
    }

    @GetMapping
    public List<Quiz> getAllQuizzes() {
        return quizRepository.findAll();
    }

    @GetMapping("/my-quizzes")
    public List<Quiz> getQuizzesByCurrentUser(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }

        // Fetch the user from the database using the username
        User user = userRepository.findByUsername(currentUser.getUsername());
        if (user == null) {
            throw new RuntimeException("User not found in the database");
        }

        return quizRepository.findByCreatedBy(user);
    }

     @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuiz(@PathVariable Long id, @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
    
        // Fetch the quiz from the database
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
    
        // Fetch the user from the database using the username
        User user = userRepository.findByUsername(currentUser.getUsername());
        if (user == null) {
            throw new RuntimeException("Authenticated user not found in the database");
        }
    
        // Check if the authenticated user is the creator of the quiz
        if (!quiz.getCreatedBy().equals(user)) {
            throw new RuntimeException("You are not authorized to delete this quiz");
        }
    
        // Delete the quiz
        quizRepository.deleteById(id);
        return ResponseEntity.ok("Quiz with ID " + id + " deleted successfully.");
    }

    @PutMapping("/{id}")
    public Quiz updateQuiz(@PathVariable Long id, @RequestBody Quiz updatedQuiz, @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
    
        // Fetch the quiz from the database
        Quiz existingQuiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
        
        // Fetch the user from the database using the username
        User user = userRepository.findByUsername(currentUser.getUsername());
        if (user == null) {
            throw new RuntimeException("User not found in the database");
        }
    
        // Check if the authenticated user is the creator of the quiz
        if (!existingQuiz.getCreatedBy().equals(user)) {
            throw new RuntimeException("You are not authorized to modify this quiz");
        }
    
        // Update the quiz details
        existingQuiz.setTitle(updatedQuiz.getTitle());
        existingQuiz.setDescription(updatedQuiz.getDescription());
        existingQuiz.setTimeLimit(updatedQuiz.getTimeLimit());
    
        // Handle questions update
        if (updatedQuiz.getQuestions() != null) {
            // Clear existing questions
            existingQuiz.getQuestions().clear();
            
            // Add new questions with proper quiz association
            for (Question question : updatedQuiz.getQuestions()) {
                question.setQuiz(existingQuiz); // Set the quiz reference
                existingQuiz.getQuestions().add(question);
            }
        }
    
        return quizRepository.save(existingQuiz);
    }

    @PostMapping("/{quizId}/submit")
    public ResponseEntity<?> submitQuizAnswers(
            @PathVariable Long quizId,
            @RequestBody Map<Long, List<Long>> answers, // Map<QuestionId, List<AnswerIds>
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {

        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }

        // Fetch the user from the database
        User user = userRepository.findByUsername(currentUser.getUsername());
        if (user == null) {
            throw new RuntimeException("Authenticated user not found in the database");
        }

        // Fetch the quiz
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        // Initialize the score
        int totalScore = 0;

        // Process answers and calculate score
        for (Map.Entry<Long, List<Long>> entry : answers.entrySet()) {
            Long questionId = entry.getKey();
            List<Long> answerIds = entry.getValue();

            // Fetch the question
            Question question = quiz.getQuestions().stream()
                    .filter(q -> q.getId().equals(questionId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Question not found"));

            // Fetch all correct answers for the question
            List<Long> correctAnswerIds = question.getAnswers().stream()
                    .filter(Answer::correct)
                    .map(Answer::getId)
                    .collect(Collectors.toList());

            // Check if the user's answers match all correct answers
            if (correctAnswerIds.size() == answerIds.size() && correctAnswerIds.containsAll(answerIds)) {
                totalScore += question.getPoints(); // Add the points for the question
            }

            // Save each user answer in the UserAnswer table
            for (Long answerId : answerIds) {
                Answer answer = question.getAnswers().stream()
                        .filter(a -> a.getId().equals(answerId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Answer not found"));

                UserAnswer userAnswer = new UserAnswer();
                userAnswer.setUser(user);
                userAnswer.setQuiz(quiz);
                userAnswer.setQuestion(question);
                userAnswer.setAnswer(answer);
                userAnswer.setCorrect(answer.correct());

                userAnswerRepository.save(userAnswer);
            }
        }

        // Save the result in the Participant table
        Participant participant = new Participant();
        participant.setUser(user);
        participant.setQuiz(quiz);
        participant.setScore(totalScore); // Save the calculated score
        participant.setCompletionTime(LocalDateTime.now().withNano(0)); // Remove milliseconds
        participantRepository.save(participant);

        return ResponseEntity.ok("Answers submitted successfully. Your score: " + totalScore);
    }

    @GetMapping("/completed")
    public ResponseEntity<?> getCompletedQuizzes(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
    
        // Fetch the user from the database
        User user = userRepository.findByUsername(currentUser.getUsername());
        if (user == null) {
            throw new RuntimeException("Authenticated user not found in the database");
        }
    
        // Fetch completed quizzes from the Participant table
        List<Participant> completedQuizzes = participantRepository.findByUser(user);
    
        // Map the results to a response format
        List<Map<String, Object>> response = completedQuizzes.stream().map(participant -> {
            Map<String, Object> map = new HashMap<>();
            map.put("quizId", participant.getQuiz().getId());
            map.put("quizTitle", participant.getQuiz().getTitle());
            map.put("score", participant.getScore());
            map.put("completionTime", participant.getCompletionTime());
            return map;
        }).collect(Collectors.toList());
    
        return ResponseEntity.ok(response);
    }
}