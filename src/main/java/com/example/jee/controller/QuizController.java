package com.example.jee.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jee.beans.Question;
import com.example.jee.beans.Quiz;
import com.example.jee.beans.User;
import com.example.jee.repository.QuizRepository;
import com.example.jee.repository.UserRepository;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private static final Logger logger = LoggerFactory.getLogger(QuizController.class);

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private UserRepository userRepository;

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
    public void deleteQuiz(@PathVariable Long id) {
        if (quizRepository.existsById(id)) {
            quizRepository.deleteById(id);
            System.out.println("Quiz with ID " + id + " deleted successfully.");
        } else {
            System.out.println("Quiz with ID " + id + " not found.");
        }
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
}