export interface Task {
    id: number,
    title: string,
    description: string,
    added: string,
    completed: boolean,
}

export interface Attachment {
    filename: string,
    id: string,
    contentType: string,
    taskId: string
}