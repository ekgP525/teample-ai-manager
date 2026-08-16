export interface Todo {
  name: string;
  task: string;
  deadline: string;
}

export interface MinutesEvidence {
  title: string;
  topic: string;
  discussions: string[];
  decisions: string[];
  pending: string[];
  todos: string[];
  nextAgenda: string[];
}

export interface Minutes {
  id: string;
  title: string;
  topic: string;
  discussions: string[];
  decisions: string[];
  pending: string[];
  todos: Todo[];
  nextAgenda: string[];
  evidence: MinutesEvidence | null;
}

export interface MinutesSummary {
  id: string;
  title: string;
  subject: string;
  meetingDate: string;
  topic: string;
  createdAt: string;
}

export interface Project {
  id: string;
  name: string;
  members: string[];
  createdAt: string;
  endDate: string | null;
  disposalDeadline: string | null;
  status: "ACTIVE" | "DISPOSAL_SCHEDULED" | "DISPOSED" | "DELETED";
  endedAt: string | null;
  disposedAt: string | null;
  deletedAt: string | null;
}

export interface ProjectTodo {
  id: string;
  content: string;
  assignee: {
    id: string;
    name: string;
  };
  meetingNoteId: string | null;
  status: "TODO" | "COMPLETED";
  priorityOrder: number;
  dueDate: string | null;
  completedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}
