import {useSearchParams} from "react-router-dom";
import {useEffect, useState} from "react";
import type {Attachment, Task} from "../Objects.tsx";
import '../App.css';
import './TaskDetails.css';
import ErrorPopup from "../components/ErrorPopup.tsx";
import DeleteIcon from "../components/DeleteIcon.tsx";
import PlusIcon from "../components/PlusIcon.tsx";

function TaskDetails() {
    const [searchParams] = useSearchParams();
    const id = searchParams.get("id") || "";

    const [task, setTask] = useState<Task | null>(null);
    const [attachments, setAttachments] = useState<Attachment[] | null>(null);
    const [error, setError] = useState<string | null>(null)
    const [fail, setFail] = useState(false);

    useEffect(() => {
        fetch(`/api/tasks/${id}`)
            .then(r => {
                if (r.status == 401)
                    window.location.href = `/login?redirect=${encodeURIComponent(window.location.href)}`
                return r.json()
            })
            .then(r => setTask(r))
            .catch(e => {
                setFail(true);
                console.error(e)
            })
    }, [id]);
    useEffect(() => {
        fetch(`/api/tasks/${id}/attachments`)
            .then(r => {
                if (r.status == 401)
                    window.location.href = `/login?redirect=${encodeURIComponent(window.location.href)}`
                return r.json()
            })
            .then(r => setAttachments(r))
            .catch(e => {
                setError("failed to load attachments")
                console.error(e)
            })
    }, [id]);

    if (fail) {
        return (
            <>
                <div className="column">
                    <h1 className="fullWidth">Task Not Found</h1>
                    <button className="button addButton" onClick={() => window.location.href = "/dashboard"}>
                        Back
                    </button>
                </div>
            </>
        );
    }

    return task !== null && (
        <>
            <ErrorPopup error={error} onClose={() => setError(null)} />
            <div className="column">
                <h1 className="fullWidth">{task.title}</h1>
                <button className="button addButton" onClick={() => window.location.href = `/editTask?id=${task.id}`}>
                    Edit
                </button>
            </div>
            <div className="column">
                <div className="taskDetail">
                    <div className="taskBody">
                        <div className="taskField descriptionField">
                            <span className="fieldLabel">Description</span>
                            <p className="fieldValue">{task.description}</p>
                        </div>
                        <div className="taskField">
                            <span className="fieldLabel attachmentHeader">
                                Attachments
                                <div style={{cursor: "pointer", placeContent: "center", placeItems: "center", display: "flex"}} onClick={() => {
                                    const input = document.createElement("input");
                                    input.type = "file";
                                    input.onchange = () => {
                                        const file = input.files?.[0];
                                        if (!file) return;
                                        const formData = new FormData();
                                        formData.append("file", file);
                                        fetch(`/api/tasks/${task.id}/attachments`, {method: "POST", body: formData})
                                            .then(r => {
                                                if (r.status == 401)
                                                    window.location.href = `/login?redirect=${encodeURIComponent(window.location.href)}`
                                                if (!r.ok)
                                                    r.text().then(x => setError(`Failed to upload attachment: ${x}`))
                                                else
                                                    r.json().then(x => setAttachments(x));
                                            })
                                            .catch(e => {
                                                console.error(e);
                                                setError("Failed to upload attachment")
                                            })
                                    };
                                    input.click();
                                }}>
                                    <PlusIcon/>
                                </div>
                            </span>
                            {attachments !== null && (
                                attachments.length > 0 ? (
                                    attachments.map((attachment: Attachment) => (
                                        <div className="attachmentColumn">
                                            <div className="deleteIcon" style={{cursor: "pointer"}} onClick={() => {
                                                fetch(`/attachments/${attachment.id}`, {method: "DELETE"})
                                                    .then(r => {
                                                        if (r.status == 401)
                                                            window.location.href = `/login?redirect=${encodeURIComponent(window.location.href)}`
                                                        if (!r.ok)
                                                            r.text().then(x => setError(`Failed to delete attachment: ${x}`))
                                                        else
                                                            r.json().then(x => setAttachments(x));
                                                    })
                                                    .catch(e => {
                                                        console.error(e);
                                                        setError("Failed to delete attachment")
                                                    })
                                            }}><DeleteIcon/></div>
                                            <span className="attachmentFilename" style={{cursor: "pointer"}} onClick={() => {
                                                window.location.href = `/attachments/${attachment.id}`
                                            }}>{attachment.filename}</span>
                                        </div>
                                    ))
                                ) : (
                                    <div>No attachments</div>
                                )
                            )}
                        </div>
                    </div>
                    <div className="taskMeta">
                        <div className="taskField">
                            <span className="fieldLabel">Status</span>
                            <div className="column taskStatus">
                                <input
                                    type="checkbox"
                                    className="checkbox"
                                    checked={task.completed}
                                    onChange={() => {
                                        const newValue = !task.completed
                                        setTask({ ...task, completed: newValue });
                                        fetch(`/api/tasks/${task.id}`, {
                                            method: "PATCH",
                                            body: JSON.stringify({completed: newValue})
                                        }).then(r => {
                                            if (!r.ok)
                                                r.text().then(x => setError(`Failed to update task state: ${x}`))
                                        }).catch(e => {
                                            console.error(e);
                                            setError("Failed to connect to server")
                                        })
                                    }} />
                                <span>{task.completed ? "Completed" : "Incomplete"}</span>
                            </div>
                        </div>
                        <div className="taskField">
                            <span className="fieldLabel">Added</span>
                            <span>{task.added}</span>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}

export default TaskDetails;