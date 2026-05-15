import {useEffect, useState} from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './App.css'

function MainPage() {
  const [loggedIn, setLoggedIn] = useState(false)

  useEffect(() => {
    fetch("/api/validate")
        .then(r => {
          if (r.ok)
            setLoggedIn(true)
        })
  })

  return (
    <>
      <section id="center">
        <div>
          <h1>The Best Todo App Ever</h1>
        </div>
        <button
          type="button"
          className="counter"
          onClick={() => {
            window.location.href = loggedIn ? "/dashboard" : "/login"
          }}
        >
          {loggedIn ? "Go to dashboard" : "Login"}
        </button>
      </section>

      <div className="ticks"></div>
      <section id="spacer"></section>
    </>
  )
}

function Dashboard() {
  const tasks = [{
      id: 1,
      completed: true,
      title: 'Task 1',
      description: 'Description 1',
      added: 'May 15, 2026'
  }, {
      id: 2,
      completed: false,
      title: 'Task 2',
      description: 'Description 2adfgkjaldskfjlhakdfjhlkadj;lfhkaj;dlfkhjladkfjlhkajfkljashgdlfkhjladkfjlhkajfkljashg',
      added: 'May 15, 2026'
  }];

  return (
    <>
      <div className="column">
        <h1 style={{width: "100%"}}>Your Tasks</h1>
        <button
            type="button"
            className="counter addButton"
            onClick={() => {
              window.location.href = "/newNote"
            }}>
          + Add
        </button>
      </div>
      <div className="column">
        <table className="tasks">
          <thead>
            <tr>
              <th scope="col" style={{width: "48px"}}></th>
              <th scope="col">title</th>
              <th scope="col">description</th>
              <th scope="col" style={{width: "150px"}}>added</th>
              <th scope="col" style={{width: "75px"}}></th>
            </tr>
          </thead>
          <tbody>
          {tasks.length == 0 ? (
              <tr>
                <td colSpan={4} style={{textAlign: "center"}}>
                  No tasks yet. Try adding one!
                </td>
              </tr>
          ) : tasks.map((task) => (
              <tr key={task.id}>
                <td style={{textAlign: 'center'}}>{task.completed ? "✔️" : ""}</td>
                <td>{task.title}</td>
                <td>
                    <div style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 1,
                        WebkitBoxOrient: 'vertical',
                        WebkitLineBreak: 'normal',
                        width: 'auto'
                    }}>{task.description}</div>
                </td>
                <td>{task.added}</td>
                <td style={{textAlign: 'center', justifyContent: 'center'}}>
                    <button className="counter" onClick={() => {
                        window.location.href = "/task?id=" + task.id;
                    }}>
                        Open
                    </button>
                </td>
              </tr>
              )
          )}
          </tbody>
        </table>
      </div>
    </>
  )
}

function NewNote() {
    return (
        <>
            <div className="column">
                <h1 style={{width: "100%"}}>New Note</h1>
                <button
                    type="button"
                    className="counter addButton"
                    onClick={() => {
                        window.location.href = "/newNote"
                    }}>
                    Save
                </button>
            </div>
        </>
    )
}

function App() {
  return <BrowserRouter>
      <Routes>
          <Route index element={<MainPage />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="mewNote" element={<NewNote />} />
      </Routes>
  </BrowserRouter>
}

export default App
