export interface Todo {
  name: string;
  task: string;
  deadline: string;
}

export interface Minutes {
  id: string;
  topic: string;
  discussions: string[];
  decisions: string[];
  pending: string[];
  todos: Todo[];
  nextAgenda: string[];
}

export interface MinutesSummary {
  id: string;
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
}
