export type DeadlineStatus =
  | "ON_TRACK"
  | "OVERDUE"
  | "NO_DEADLINE"
  | "UNKNOWN_DEADLINE";

export interface TodoAssignment {
  assignmentId: string;
  todoId: string;
  projectId: string;
  projectName: string;
  minutesId: string;
  minutesTitle: string;
  userId: string;
  memberName: string;
  task: string;
  deadline: string | null;
  assigned: boolean;
  completed: boolean;
  status: "TODO" | "DONE";
  completedAt: string | null;
  deadlineStatus: DeadlineStatus;
}

export interface MyProjectDashboard {
  projectId: string;
  projectName: string;
  userId: string;
  memberName: string;
  target: string;
  totalTodoCount: number;
  completedTodoCount: number;
  pendingTodoCount: number;
  progressRate: number;
  todos: TodoAssignment[];
}

export interface TeamMemberProgress {
  userId: string;
  memberName: string;
  totalTodoCount: number;
  completedTodoCount: number;
  pendingTodoCount: number;
  progressRate: number;
  todos: TodoAssignment[];
}

export interface TeamTodoProgress {
  todoId: string;
  projectId: string;
  projectName: string;
  minutesId: string;
  minutesTitle: string;
  task: string;
  deadline: string | null;
  sourceAssignee: string | null;
  assignments: TodoAssignment[];
}

export interface TeamProjectDashboard {
  projectId: string;
  projectName: string;
  members: TeamMemberProgress[];
  todos: TeamTodoProgress[];
}
