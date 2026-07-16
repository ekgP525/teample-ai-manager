export interface Todo {
  name: string;
  task: string;
  deadline: string;
}

export interface Minutes {
  id: string;
  meetingId: string;
  summary: string;
  decisions: string[];
  todos: Todo[];
  pending: string[];
}

export interface MeetingInput {
  subject: string;
  meetingDate: string;
  members: string[];
  rawText: string;
}
