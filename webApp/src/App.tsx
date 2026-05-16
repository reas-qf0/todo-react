import {BrowserRouter, Routes, Route} from 'react-router-dom'
import './App.css'
import MainPage from "./pages/MainPage.tsx";
import Dashboard from "./pages/Dashboard.tsx";
import NewTask from "./pages/NewTask.tsx";
import TaskDetails from "./pages/TaskDetails.tsx";
import EditTask from "./pages/EditTask.tsx";

function App() {
    return <BrowserRouter>
        <Routes>
            <Route index element={<MainPage/>}/>
            <Route path="dashboard" element={<Dashboard/>}/>
            <Route path="newTask" element={<NewTask/>}/>
            <Route path="task" element={<TaskDetails/>}/>
            <Route path="editTask" element={<EditTask/>}/>
        </Routes>
    </BrowserRouter>
}

export default App
