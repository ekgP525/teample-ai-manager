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

export interface MeetingInput {
  subject: string;
  meetingDate: string;
  members: string[];
  rawText: string;
}
