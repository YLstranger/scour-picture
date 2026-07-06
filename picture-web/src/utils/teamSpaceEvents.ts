const TEAM_SPACE_CHANGED_EVENT = 'team-space-changed'

export const notifyTeamSpaceChanged = () => {
  window.dispatchEvent(new Event(TEAM_SPACE_CHANGED_EVENT))
}

export const onTeamSpaceChanged = (handler: () => void) => {
  window.addEventListener(TEAM_SPACE_CHANGED_EVENT, handler)
}

export const offTeamSpaceChanged = (handler: () => void) => {
  window.removeEventListener(TEAM_SPACE_CHANGED_EVENT, handler)
}
