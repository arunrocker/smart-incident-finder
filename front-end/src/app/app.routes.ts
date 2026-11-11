import { Routes } from '@angular/router';
import { IncidentSearchComponent } from './components/incident-search/incident-search.component';

export const routes: Routes = [
  { path: '', component: IncidentSearchComponent },
  { path: '**', redirectTo: '' },
];
