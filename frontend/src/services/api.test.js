import apiClient, { erpAPI, wizardAPI } from './api';
jest.mock('axios', () => ({ create: jest.fn(() => ({
  get:jest.fn(), post:jest.fn(), put:jest.fn(), delete:jest.fn(),
  interceptors:{request:{use:jest.fn()},response:{use:jest.fn()}},
})) }));
const rejectResponse = apiClient.interceptors.response.use.mock.calls[0][1];
afterEach(() => jest.restoreAllMocks());

test('cancellation uses the session DELETE contract and returns its confirmation', async () => {
  const confirmation={status:'CANCELLED',tempModuleId:'session-a'};
  apiClient.delete.mockResolvedValue({data:confirmation});
  expect(await wizardAPI.cancelIncorporation('session-a')).toEqual(confirmation);
  expect(apiClient.delete).toHaveBeenCalledWith('/wizard/session-a');
});

test('ERP update does not echo a read-only credential indicator or overwrite a secret with an empty field', async () => {
  apiClient.put.mockResolvedValue({data:{id:'erp-a',hasPassword:true}});
  const config={name:'Renamed',password:'',hasPassword:true};
  await erpAPI.updateConnector('erp-a',config);
  expect(apiClient.put).toHaveBeenLastCalledWith('/erp/connectors/erp-a',{name:'Renamed'});
  expect(config).toEqual({name:'Renamed',password:'',hasPassword:true});
  await erpAPI.updateConnector('erp-a',{name:'Renamed',password:'new-synthetic-secret',hasPassword:true});
  expect(apiClient.put).toHaveBeenLastCalledWith('/erp/connectors/erp-a',{name:'Renamed',password:'new-synthetic-secret'});
});

test('API error logging never prints an echoed response secret', async () => {
  const log=jest.spyOn(console,'error').mockImplementation(()=>{});
  const error={response:{status:422,data:{password:'synthetic-secret'}}};
  await expect(rejectResponse(error)).rejects.toBe(error);
  expect(log).toHaveBeenCalledWith('API request failed',422);
  expect(JSON.stringify(log.mock.calls)).not.toContain('synthetic-secret');
});
