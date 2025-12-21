# Exam Scheduler System
This project is developed for the SE302 – Principles of Software Engineering course.

The system automatically schedules exams by considering student enrollments,
classroom capacities, and available time slots while avoiding student conflicts.
## Features
- Reads students, courses, classrooms, and enrollment data
- Builds a conflict graph based on shared students
- Prioritizes courses with higher conflict degrees
- Assigns classrooms according to student counts
- Generates a feasible global exam schedule
## Technologies Used
- Java
- JavaFX
- Gradle
- SQLite
- Object-Oriented Design
## Project Structure

```
scheduler/
 ├── assign/        Exam placement and classroom assignment logic
 ├── config/        System configuration and scheduling parameters
 ├── constraints/   Scheduling constraints and validation rules
 ├── core/          Core scheduling flow and orchestration logic
 ├── dao/           Database access layer (SQLite)
 ├── export/        Exporting generated schedules
 ├── io/            Input reading and data loading operations
 ├── model/         Domain models (Student, Course, Classroom, Timeslot)
 └── ui/            User interface and application entry point
```

## How to Run

### Option 1: Run as Executable (Recommended)
1. Download the executable file provided with the project.
2. Double-click the `.exe` file on the desktop.
3. The application starts without requiring an IDE.

> Note: Java Runtime Environment (JRE) must be installed on the system.

---

### Option 2: Run from IDE (Developer Mode)
1. Open the project in IntelliJ IDEA.
2. Ensure Java SDK is properly configured.
3. Build the project using Gradle.
4. Run the main application class.

## Input
- Student list
- Course list
- Classroom list
- Enrollment data

## Output
- Exam schedule with assigned classrooms and time slots
## Notes
- Scheduling is enrollment-driven
- Student conflicts are avoided
- Design and implementation are developed iteratively
